package com.uip.analytics.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

@Slf4j
@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.url}")
    private String clickhouseUrl;

    @Value("${clickhouse.username:default}")
    private String username;

    @Value("${clickhouse.password:}")
    private String password;

    @Value("${clickhouse.database:analytics}")
    private String database;

    /**
     * ClickHouse session id for this application instance — pins HTTP-mode JDBC
     * connections so that {@code SET SQL_tenant_id = ...} issued by
     * {@code RowPolicyEngine} persists across the SET statement and the
     * subsequent SELECT. Without a session_id the HTTP interface is stateless
     * and each statement runs in a fresh session (the SET is lost).
     *
     * <p>The same id is reused for every pooled connection. Cross-request tenant
     * bleed is prevented by {@code RowPolicyEngine}'s try/finally RESET — every
     * borrower restores {@code SQL_tenant_id} to empty before the connection is
     * returned. CH also expires idle sessions after {@code session_timeout}
     * (default 60s).</p>
     *
     * <p>Ref: ADR-047, regression fix M5-1-T10.
     */
    @Value("${clickhouse.session_id:uip-analytics-${random.uuid}}")
    private String sessionId;

    @Bean
    public DataSource clickHouseDataSource() throws SQLException {
        String url = clickhouseUrl;
        if (!url.contains("/analytics") && !url.contains("?database=")) {
            url = url.endsWith("/") ? url + database : url + "/" + database;
        }

        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("socket_timeout", "30000");
        props.setProperty("connect_timeout", "10000");
        props.setProperty("compress", "0");
        // ADR-047 (regression fix M5-1-T10): pin a session id so HTTP-mode JDBC
        // connections carry session state (SET SQL_tenant_id) across statements.
        props.setProperty("session_id", sessionId);

        log.info("Connecting to ClickHouse: {} (session_id={})", url, sessionId);
        return new ClickHouseDataSource(url, props);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public HealthIndicator clickHouseHealthIndicator(DataSource clickHouseDataSource) {
        return () -> {
            try (var conn = clickHouseDataSource.getConnection();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    return Health.up().withDetail("database", database).build();
                }
                return Health.down().withDetail("error", "Empty result from SELECT 1").build();
            } catch (Exception e) {
                return Health.down(e).withDetail("database", database).build();
            }
        };
    }
}
