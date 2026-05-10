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
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("ClickHouseEnergyRepository — integration test with Testcontainers")
class ClickHouseEnergyRepositoryIT {

    // GenericContainer avoids Testcontainers ClickHouseContainer JDBC health-check
    // incompatibility with clickhouse-jdbc 0.6.x
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> clickhouse = new GenericContainer<>("clickhouse/clickhouse-server:23.8")
            .withExposedPorts(8123)
            .waitingFor(Wait.forHttp("/ping").forPort(8123).withStartupTimeout(Duration.ofSeconds(60)));

    static ClickHouseEnergyRepository repository;

    @BeforeAll
    static void setup() throws Exception {
        String host = clickhouse.getHost();
        int port = clickhouse.getMappedPort(8123);

        // Use HTTP API for DDL — JdbcTemplate.execute() triggers getUpdateCount() which
        // ClickHouse JDBC 0.6.x throws UnsupportedOperationException for DDL statements
        httpPost(host, port, """
                CREATE TABLE IF NOT EXISTS energy_readings (
                    tenant_id    String,
                    building_id  String,
                    kwh          Float64,
                    demand_kw    Float64,
                    power_factor Float64,
                    ts           Int64
                ) ENGINE = MergeTree()
                ORDER BY (tenant_id, building_id, ts)
                """);

        // Seed data via HTTP INSERT
        httpPost(host, port,
                "INSERT INTO energy_readings VALUES ('t1','B1',100.0,50.0,0.9,1000)");
        httpPost(host, port,
                "INSERT INTO energy_readings VALUES ('t1','B1',200.0,60.0,0.85,1500)");
        httpPost(host, port,
                "INSERT INTO energy_readings VALUES ('t1','B2',150.0,45.0,0.92,1200)");
        httpPost(host, port,
                "INSERT INTO energy_readings VALUES ('t2','B3',999.0,999.0,0.5,1300)");

        // Give ClickHouse a moment to flush MergeTree parts
        Thread.sleep(500);

        // Build JdbcTemplate — used by the repository for SELECT queries (no DDL)
        String url = "jdbc:clickhouse://" + host + ":" + port;
        Properties props = new Properties();
        props.setProperty("user", "default");
        props.setProperty("password", "");
        props.setProperty("compress", "0");   // LZ4 not on classpath in test env
        DataSource ds = new ClickHouseDataSource(url, props);
        repository = new ClickHouseEnergyRepository(new JdbcTemplate(ds));
    }

    /** POST a SQL statement to ClickHouse HTTP interface. */
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
    @DisplayName("aggregateByBuilding returns sum/max per building for tenant")
    void aggregateByBuilding_sumAndPeak() {
        List<BuildingEnergyBreakdown> result = repository.aggregateByBuilding(
                "t1", List.of(), 0L, 9999L);

        assertThat(result).hasSize(2);
        BuildingEnergyBreakdown b1 = result.stream()
                .filter(b -> b.buildingId().equals("B1")).findFirst().orElseThrow();
        assertThat(b1.totalKwh()).isEqualTo(300.0);
        assertThat(b1.peakDemandKw()).isEqualTo(60.0);
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
    @DisplayName("aggregatePowerFactor returns average across buildings")
    void aggregatePowerFactor_average() throws SQLException {
        double pf = repository.aggregatePowerFactor("t1", List.of(), 0L, 9999L);

        // (0.9 + 0.85 + 0.92) / 3 ≈ 0.89
        assertThat(pf).isBetween(0.87, 0.91);
    }
}
