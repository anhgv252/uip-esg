package com.uip.analytics.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("database", database);
        props.setProperty("socket_timeout", "30000");
        props.setProperty("connect_timeout", "10000");
        // Disable LZ4 compression — requires lz4-java on classpath; use gzip in production
        // if bandwidth is a concern: add lz4-java dependency and remove this line
        props.setProperty("compress", "0");

        log.info("Connecting to ClickHouse: {}", clickhouseUrl);
        return new ClickHouseDataSource(clickhouseUrl, props);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
