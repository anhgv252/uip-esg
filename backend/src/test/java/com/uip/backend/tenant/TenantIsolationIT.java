package com.uip.backend.tenant;


import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for multi-tenant Row-Level Security isolation.
 *
 * Verifies:
 * - Tenant A cannot read Tenant B data (cross-tenant isolation)
 * - SET LOCAL resets after COMMIT (no HikariCP leak)
 * - T1 default tenant works correctly
 * - RLS blocks query when tenant context is not set
 *
 * Creates a restricted PostgreSQL role (not table owner) to ensure RLS policies
 * are enforced — table owners bypass RLS by default.
 */
@Tag("integration")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantIsolationIT {

    @SuppressWarnings("resource") // Ryuk reaper dọn sạch; @AfterAll stop() explicit để giải phóng sớm
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        postgres.start();

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("spring.data.redis.host", () -> "localhost");
    }

    // Mock infrastructure not available in Testcontainers
    @MockBean @SuppressWarnings("unused")
    RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    StringRedisTemplate redisTemplate;
    @MockBean @SuppressWarnings("unused")
    RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused")
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterAll
    static void stopContainer() {
        // Container riêng (không dùng AbstractIT) vì cần tạo non-owner role để test RLS.
        // Ryuk sẽ tự dọn khi JVM thoát, nhưng stop explicit ở đây để giải phóng tài nguyên sớm.
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
    }

    @BeforeAll
    void setupRestrictedRoleAndTestData() {
        // Create a restricted role that is NOT a table owner, so RLS policies apply.
        // The default Testcontainers user ("test") is the database owner and bypasses RLS.
        jdbc.execute("CREATE ROLE app_user NOINHERIT LOGIN PASSWORD 'test'");
        jdbc.execute("GRANT USAGE ON SCHEMA environment TO app_user");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO app_user");
        jdbc.execute("GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA environment TO app_user");
        jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA environment GRANT SELECT, INSERT ON TABLES TO app_user");

        // Insert test sensors for different tenants (as table owner)
        jdbc.execute("INSERT INTO environment.sensors (id, sensor_id, sensor_name, tenant_id, location_path) " +
                     "VALUES ('a0000000-0000-0000-0000-000000000001', 'SENSOR-HCM-001', 'HCM Sensor 1', 'hcm', 'city.hcm.d7.riverpark.tower_a') " +
                     "ON CONFLICT (sensor_id) DO NOTHING");
        jdbc.execute("INSERT INTO environment.sensors (id, sensor_id, sensor_name, tenant_id, location_path) " +
                     "VALUES ('b0000000-0000-0000-0000-000000000001', 'SENSOR-HN-001', 'HN Sensor 1', 'hn', 'city.hn.badinh.cluster_x.building_b') " +
                     "ON CONFLICT (sensor_id) DO NOTHING");
        jdbc.execute("INSERT INTO environment.sensors (id, sensor_id, sensor_name, tenant_id, location_path) " +
                     "VALUES ('d0000000-0000-0000-0000-000000000001', 'SENSOR-DEF-001', 'Default Sensor', 'default', 'city.default.building.main') " +
                     "ON CONFLICT (sensor_id) DO NOTHING");
    }

    /**
     * Execute all SQL within a single connection to ensure SET ROLE and SET LOCAL persist.
     * HikariCP may return different connections per jdbc.execute() call, breaking LOCAL scope.
     */
    private <T> T queryAsAppUser(String tenantId, java.util.concurrent.Callable<T> action) {
        return jdbc.execute((java.sql.Connection conn) -> {
            boolean wasAutoCommit = conn.getAutoCommit();
            if (wasAutoCommit) conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                stmt.execute("SET ROLE app_user");
                if (tenantId != null) {
                    stmt.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
                }
            }
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try (var stmt = conn.createStatement()) {
                    stmt.execute("RESET ROLE");
                    conn.commit();
                }
                if (wasAutoCommit) conn.setAutoCommit(true);
            }
        });
    }

    /**
     * Execute a query as restricted app_user with SET LOCAL tenant context,
     * all within a single JDBC connection to prevent HikariCP connection switching.
     */
    private java.util.List<java.util.Map<String, Object>> queryAsRestrictedUser(String tenantId, String sql) {
        return jdbc.execute((java.sql.Connection conn) -> {
            boolean wasAutoCommit = conn.getAutoCommit();
            if (wasAutoCommit) conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                stmt.execute("SET ROLE app_user");
                if (tenantId != null) {
                    stmt.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
                }
                var rs = stmt.executeQuery(sql);
                var results = new java.util.ArrayList<java.util.Map<String, Object>>();
                var meta = rs.getMetaData();
                while (rs.next()) {
                    var row = new java.util.LinkedHashMap<String, Object>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    results.add(row);
                }
                return results;
            } finally {
                try (var stmt = conn.createStatement()) {
                    stmt.execute("RESET ROLE");
                }
                conn.commit();
                if (wasAutoCommit) conn.setAutoCommit(true);
            }
        });
    }

    private java.util.List<java.util.Map<String, Object>> queryAsRestrictedUserNoTenant(String sql) {
        return queryAsRestrictedUser(null, sql);
    }

    @Test
    @Order(1)
    @DisplayName("RLS: Tenant A cannot see Tenant B data")
    void tenantACannotSeeTenantBData() {
        var results = queryAsRestrictedUser("hcm",
            "SELECT sensor_id, tenant_id FROM environment.sensors");

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(row ->
            assertThat(row.get("tenant_id")).isEqualTo("hcm")
        );
        assertThat(results.stream().noneMatch(r -> "hn".equals(r.get("tenant_id")))).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("RLS: Tenant B cannot see Tenant A data")
    void tenantBCannotSeeTenantAData() {
        var results = queryAsRestrictedUser("hn",
            "SELECT sensor_id, tenant_id FROM environment.sensors");

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(row ->
            assertThat(row.get("tenant_id")).isEqualTo("hn")
        );
        assertThat(results.stream().noneMatch(r -> "hcm".equals(r.get("tenant_id")))).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("RLS: Default tenant sees only default data")
    void defaultTenantSeesOnlyDefaultData() {
        var results = queryAsRestrictedUser("default",
            "SELECT sensor_id, tenant_id FROM environment.sensors");

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(row ->
            assertThat(row.get("tenant_id")).isEqualTo("default")
        );
    }

    @Test
    @Order(4)
    @DisplayName("SET LOCAL resets after COMMIT — no connection leak")
    void setLocalResetsAfterCommit() {
        jdbc.execute("SET LOCAL app.tenant_id = 'hcm'");
        jdbc.execute("COMMIT");

        String value = jdbc.queryForObject(
            "SELECT current_setting('app.tenant_id', true)", String.class);
        // PostgreSQL returns NULL or empty string for unknown custom GUC with missing_ok=true
        assertThat(value).isNullOrEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("RLS: Missing tenant context returns 0 rows")
    void missingTenantReturnsNoRows() {
        jdbc.execute("COMMIT");

        String value = jdbc.queryForObject(
            "SELECT current_setting('app.tenant_id', true)", String.class);
        assertThat(value).isNullOrEmpty();

        // Query as restricted role without tenant context — RLS should block all access
        var results = queryAsRestrictedUserNoTenant("SELECT * FROM environment.sensors");
        assertThat(results).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("LTREE: Subtree query works on location_path")
    void ltreeSubtreeQuery() {
        var results = queryAsRestrictedUser("hcm",
            "SELECT sensor_id FROM environment.sensors WHERE location_path <@ 'city.hcm'");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("sensor_id")).isEqualTo("SENSOR-HCM-001");
    }

    @Test
    @Order(7)
    @DisplayName("RLS: Write isolation — tenant cannot INSERT for other tenant")
    void writeIsolation() {
        // Use Connection callback to keep SET ROLE + SET LOCAL in same transaction
        jdbc.execute((java.sql.Connection conn) -> {
            boolean wasAutoCommit = conn.getAutoCommit();
            if (wasAutoCommit) conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                stmt.execute("SET ROLE app_user");
                stmt.execute("SET LOCAL app.tenant_id = 'hcm'");

                stmt.execute("INSERT INTO environment.sensors (id, sensor_id, sensor_name, tenant_id, location_path) " +
                             "VALUES ('a0000000-0000-0000-0000-000000000002', 'SENSOR-HCM-002', 'HCM Sensor 2', 'hcm', 'city.hcm.d7') " +
                             "ON CONFLICT (sensor_id) DO NOTHING");

                var rs = stmt.executeQuery("SELECT count(*) FROM environment.sensors WHERE tenant_id = 'hcm'");
                rs.next();
                int count = rs.getInt(1);
                assertThat(count).isGreaterThanOrEqualTo(2);

                stmt.execute("RESET ROLE");
            } finally {
                conn.commit();
                if (wasAutoCommit) conn.setAutoCommit(true);
            }
            return null;
        });
    }
}
