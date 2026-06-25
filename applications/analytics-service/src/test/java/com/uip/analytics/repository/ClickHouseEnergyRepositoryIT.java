package com.uip.analytics.repository;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("ClickHouseEnergyRepository — integration test with Testcontainers")
class ClickHouseEnergyRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> clickhouse = new GenericContainer<>("clickhouse/clickhouse-server:23.8")
            .withExposedPorts(8123)
            // NOTE (M5-1-T10): no custom config XML is mounted — CH 23.8 requires
            // the SQL_ prefix on user-defined settings (handled in RowPolicyEngine),
            // and declaring them in <profiles> crashes the server. See V032 header.
            .waitingFor(Wait.forHttp("/ping").forPort(8123).withStartupTimeout(Duration.ofSeconds(60)));

    static ClickHouseEnergyRepository repository;

    @BeforeAll
    static void setup() throws Exception {
        String host = clickhouse.getHost();
        int port = clickhouse.getMappedPort(8123);

        // Create database + table matching V001__create_analytics_schema.sql
        httpPost(host, port, "CREATE DATABASE IF NOT EXISTS analytics");

        httpPost(host, port, """
                CREATE TABLE IF NOT EXISTS analytics.esg_readings (
                    tenant_id    String,
                    building_id  String,
                    source_id    String DEFAULT '',
                    metric_type  LowCardinality(String),
                    value        Float64,
                    unit         LowCardinality(String) DEFAULT '',
                    recorded_at  DateTime CODEC(DoubleDelta, ZSTD(3)),
                    ingested_at  DateTime DEFAULT now()
                ) ENGINE = MergeTree()
                PARTITION BY toYYYYMM(recorded_at)
                ORDER BY (tenant_id, building_id, source_id, metric_type, recorded_at)
                SETTINGS index_granularity = 8192
                """);

        // Seed data — matches the new schema (tenant_id, building_id, source_id, metric_type, value, unit, recorded_at)
        // Tenant t1, Building B1 — 2 ENERGY rows: values 100.0 and 200.0
        httpPost(host, port,
                "INSERT INTO analytics.esg_readings (tenant_id, building_id, source_id, metric_type, value, unit, recorded_at) " +
                "VALUES ('t1','B1','src-b1-1','ENERGY',100.0,'kWh',fromUnixTimestamp(1000))");
        httpPost(host, port,
                "INSERT INTO analytics.esg_readings (tenant_id, building_id, source_id, metric_type, value, unit, recorded_at) " +
                "VALUES ('t1','B1','src-b1-2','ENERGY',200.0,'kWh',fromUnixTimestamp(1500))");
        // Tenant t1, Building B2 — 1 ENERGY row: value 150.0
        httpPost(host, port,
                "INSERT INTO analytics.esg_readings (tenant_id, building_id, source_id, metric_type, value, unit, recorded_at) " +
                "VALUES ('t1','B2','src-b2-1','ENERGY',150.0,'kWh',fromUnixTimestamp(1200))");
        // Tenant t1, Building B1 — 1 WATER row (should be excluded by metric_type='ENERGY' filter)
        httpPost(host, port,
                "INSERT INTO analytics.esg_readings (tenant_id, building_id, source_id, metric_type, value, unit, recorded_at) " +
                "VALUES ('t1','B1','src-b1-w','WATER',50.0,'m³',fromUnixTimestamp(1100))");
        // Tenant t2, Building B3 — cross-tenant data (should not be visible to t1)
        httpPost(host, port,
                "INSERT INTO analytics.esg_readings (tenant_id, building_id, source_id, metric_type, value, unit, recorded_at) " +
                "VALUES ('t2','B3','src-b3-1','ENERGY',999.0,'kWh',fromUnixTimestamp(1300))");

        // Give ClickHouse a moment to flush MergeTree parts
        Thread.sleep(500);

        // Build JdbcTemplate pointing to the 'analytics' database
        String url = "jdbc:clickhouse://" + host + ":" + port + "/analytics";
        Properties props = new Properties();
        props.setProperty("user", "default");
        props.setProperty("password", "");
        props.setProperty("compress", "0");
        // M5-1-T10: pin a session id so the SET SQL_tenant_id issued by
        // RowPolicyEngine persists to the subsequent SELECT. The HTTP interface
        // is otherwise stateless — SET would be lost between statements.
        props.setProperty("session_id", "energy-repo-it-" + System.currentTimeMillis());
        DataSource ds = new ClickHouseDataSource(url, props);
        // ADR-047: repository now requires a RowPolicyEngine. This IT connects
        // as the privileged 'default' user (no V032 policy applied), so the
        // engine's SET SQL_tenant_id / RESET calls enforce no row filter — but
        // they MUST still execute without error (regression M5-1-T10: previously
        // SET tenant_id was rejected with Code 115). The actual cross-tenant
        // enforcement is covered by RowPolicyIsolationIT (analytics_policy user
        // + V032 policy applied).
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        repository = new ClickHouseEnergyRepository(
            jdbcTemplate, new com.uip.analytics.security.RowPolicyEngine(jdbcTemplate));
    }

    private static void httpPost(String host, int port, String sql) throws Exception {
        URL url = new URL("http://" + host + ":" + port + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(sql.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("ClickHouse HTTP error " + code + " for SQL: " + sql.substring(0, Math.min(80, sql.length())));
        }
    }

    @Test
    @DisplayName("aggregateByBuilding returns sum/max per building for tenant (ENERGY only)")
    void aggregateByBuilding_sumAndPeak() {
        List<BuildingEnergyBreakdown> result = repository.aggregateByBuilding(
                "t1", List.of(), 0L, 9999L);

        // B1: sum=300.0 (100+200), max=200.0; B2: sum=150.0, max=150.0
        // WATER row for B1 excluded by metric_type filter
        assertThat(result).hasSize(2);
        BuildingEnergyBreakdown b1 = result.stream()
                .filter(b -> b.buildingId().equals("B1")).findFirst().orElseThrow();
        assertThat(b1.totalKwh()).isEqualTo(300.0);
        assertThat(b1.peakDemandKw()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("aggregateByBuilding with buildingIds filter returns only specified buildings")
    void aggregateByBuilding_withFilter() {
        List<BuildingEnergyBreakdown> result = repository.aggregateByBuilding(
                "t1", List.of("B2"), 0L, 9999L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).buildingId()).isEqualTo("B2");
        assertThat(result.get(0).totalKwh()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("tenant isolation — t2 data not visible to t1 query")
    void tenantIsolation() {
        List<BuildingEnergyBreakdown> result = repository.aggregateByBuilding(
                "t1", List.of(), 0L, 9999L);

        assertThat(result).noneMatch(b -> b.buildingId().equals("B3"));
    }

    @Test
    @DisplayName("aggregatePowerFactor returns 1.0 (unity PF — no dedicated metric yet)")
    void aggregatePowerFactor_returnsUnity() {
        double pf = repository.aggregatePowerFactor("t1", List.of(), 0L, 9999L);
        assertThat(pf).isEqualTo(1.0);
    }

    @Test
    @DisplayName("aggregatePowerFactor — empty result set returns 1.0 default")
    void aggregatePowerFactor_emptySet_returnsDefault() {
        double pf = repository.aggregatePowerFactor("tenant-no-data", List.of(), 0L, 9999L);
        assertThat(pf).isEqualTo(1.0);
    }

    @Test
    @DisplayName("aggregateByBuilding — empty result set returns empty list")
    void aggregateByBuilding_noData_returnsEmptyList() {
        List<BuildingEnergyBreakdown> result = repository.aggregateByBuilding(
                "tenant-no-data", List.of(), 0L, 9999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("aggregateByBuilding — time range filter excludes out-of-range rows")
    void aggregateByBuilding_timeRangeFilter() {
        // Only rows with recorded_at in [0, 1100] epoch seconds
        // B1 ENERGY row at epoch 1000 (value=100.0) is included
        // B1 ENERGY row at epoch 1500 (value=200.0) is excluded
        // B2 ENERGY row at epoch 1200 (value=150.0) is excluded
        List<BuildingEnergyBreakdown> result = repository.aggregateByBuilding(
                "t1", List.of(), 0L, 1100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).buildingId()).isEqualTo("B1");
        assertThat(result.get(0).totalKwh()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("aggregateByBuilding — WATER metric_type excluded when filtering ENERGY")
    void aggregateByBuilding_waterMetricExcluded() {
        List<BuildingEnergyBreakdown> result = repository.aggregateByBuilding(
                "t1", List.of("B1"), 0L, 9999L);

        // B1 has 2 ENERGY rows (100+200=300) + 1 WATER row (excluded)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalKwh()).isEqualTo(300.0);
    }
}
