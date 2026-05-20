package com.uip.flink.esg;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Async lookup of building metadata (name, district, category) from PostgreSQL.
 * ADR-035: Flink Enrichment — Building Metadata Join Pattern.
 *
 * Uses Caffeine cache (5 min TTL, max 1000 entries) to avoid repeated JDBC lookups.
 * Building count is small (~5-100) so connection pool of 5 is sufficient.
 */
public class BuildingMetadataAsyncFunction
        extends RichAsyncFunction<NgsiLdMessage, NgsiLdMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(BuildingMetadataAsyncFunction.class);

    private static final String LOOKUP_SQL = """
            SELECT building_name, cluster_id AS district, 'COMMERCIAL' AS category
            FROM public.buildings
            WHERE building_code = ? AND is_active = true
            LIMIT 1
            """;

    private transient Cache<String, BuildingMetadata> cache;
    private transient ExecutorService executor;
    private transient javax.sql.DataSource dataSource;

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public BuildingMetadataAsyncFunction(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        cache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
                .build();
        executor = Executors.newFixedThreadPool(5);
        var pool = new com.zaxxer.hikari.HikariConfig();
        pool.setJdbcUrl(dbUrl);
        pool.setUsername(dbUser);
        pool.setPassword(dbPassword);
        pool.setMaximumPoolSize(5);
        pool.setMinimumIdle(2);
        pool.setConnectionTimeout(3_000);
        dataSource = new com.zaxxer.hikari.HikariDataSource(pool);
    }

    @Override
    public void asyncInvoke(NgsiLdMessage msg, ResultFuture<NgsiLdMessage> resultFuture) {
        CompletableFuture.supplyAsync(() -> {
            try {
                String buildingCode = EsgDualSinkJob.extractBuildingId(msg.getDeviceIdValue());
                if (buildingCode.isEmpty()) {
                    return msg;
                }

                // Check cache first
                BuildingMetadata cached = cache.getIfPresent(buildingCode);
                if (cached != null) {
                    enrichMessage(msg, cached);
                    return msg;
                }

                // Cache miss — JDBC lookup
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(LOOKUP_SQL)) {
                    ps.setString(1, buildingCode);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            BuildingMetadata bm = new BuildingMetadata(
                                    rs.getString("building_name"),
                                    rs.getString("district"),
                                    rs.getString("category")
                            );
                            cache.put(buildingCode, bm);
                            enrichMessage(msg, bm);
                        }
                    }
                }
                return msg;
            } catch (Exception e) {
                LOG.warn("Building metadata lookup failed for deviceId={}: {}",
                        msg.getDeviceIdValue(), e.getMessage());
                return msg;
            }
        }, executor).whenComplete((result, throwable) -> {
            if (throwable != null) {
                resultFuture.completeExceptionally(throwable);
            } else {
                resultFuture.complete(Collections.singleton(result));
            }
        });
    }

    private void enrichMessage(NgsiLdMessage msg, BuildingMetadata bm) {
        NgsiLdMessage.Meta meta = msg.getMeta();
        if (meta == null) {
            meta = new NgsiLdMessage.Meta();
            msg.setMeta(meta);
        }
        meta.setBuildingName(bm.buildingName());
        meta.setDistrict(bm.district());
        meta.setCategory(bm.category());
    }

    Cache<String, BuildingMetadata> getCache() {
        return cache;
    }

    @Override
    public void close() throws Exception {
        if (executor != null) executor.shutdownNow();
        if (dataSource instanceof AutoCloseable ac) ac.close();
        super.close();
    }

    public record BuildingMetadata(String buildingName, String district, String category) {}
}
