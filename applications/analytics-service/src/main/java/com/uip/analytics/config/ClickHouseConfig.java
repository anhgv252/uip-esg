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

        log.info("Connecting to ClickHouse: {}", url);
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
