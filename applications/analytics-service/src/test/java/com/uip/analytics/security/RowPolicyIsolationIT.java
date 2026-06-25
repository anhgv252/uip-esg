package com.uip.analytics.security;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for ADR-047 RowPolicy enforcement against a REAL ClickHouse
 * container (Testcontainers) with the V032 migration applied.
 *
 * <p>This is the executable artifact that closes the false-DONE on T04: T04 was
 * marked DONE relying only on {@code RowPolicyEngineTest} (a mocked-JDBC unit
 * test that never runs a real {@code SET}). This class verifies the actual
 * security guarantee end-to-end against CH 23.8:</p>
 * <ol>
 *   <li>Querying as tenant_A returns only tenant_A rows — even when the SQL
 *       accidentally OMITS the {@code WHERE tenant_id} clause (RowPolicy L2).</li>
 *   <li>Querying as tenant_B returns only tenant_B rows.</li>
 *   <li>A session with no {@code SET SQL_tenant_id} fails with
 *       {@code Code 115 UNKNOWN_SETTING} at policy-evaluation time → the SELECT
 *       errors out → <b>fail-CLOSED</b> (no rows leak).</li>
 *   <li>The {@code RowPolicyEngine} SET/RESET wrapper does NOT bleed the tenant
 *       setting across consecutive requests on the same pooled connection
 *       (Spike S1 acceptance criteria — ADR-047 §3.3).</li>
 * </ol>
 *
 * <p><b>CH 23.8 caveat (regression fix M5-1-T10).</b> The V032 RowPolicy is
 * created {@code AS PERMISSIVE}, not {@code AS RESTRICTIVE}: in CH 23.8 a
 * RESTRICTIVE policy with no PERMISSIVE sibling returns zero rows for every
 * query (verified on 23.8.16), even when the USING clause matches. PERMISSIVE
 * with a strict tenant equality is still a real isolation barrier — a row
 * whose {@code tenant_id} differs from the session setting is filtered out —
 * and is the only mode that works on 23.8.</p>
 *
 * <p>Tagged {@code "integration"} — included when CI runs the
 * {@code integrationTest} task (or via {@code pact-verify.sh}). The default
 * {@code test} task excludes this tag to keep unit-test feedback fast.</p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@DisplayName("RowPolicyIsolationIT — ADR-047 RowPolicy enforcement on real ClickHouse")
class RowPolicyIsolationIT {

    /** Dedicated policy user created by V032 — every connection runs under it. */
    private static final String POLICY_USER = "analytics_policy";
    private static final String POLICY_PASSWORD = "changeme";

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> clickhouse = new GenericContainer<>("clickhouse/clickhouse-server:23.8")
            .withExposedPorts(8123)
            // Enable access management so V032 can CREATE USER / ROW POLICY.
            // NOTE: no custom config XML is mounted — `SQL_tenant_id` is a
            // runtime-only setting in CH 23.8; declaring it in <profiles> crashes
            // the server. See V032 header for the full rationale.
            .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
            .waitingFor(Wait.forHttp("/ping").forPort(8123).withStartupTimeout(Duration.ofSeconds(60)));

    private String httpUrl;
    private String policyJdbcUrl;

    @BeforeAll
    void setup() throws Exception {
        String host = clickhouse.getHost();
        int port = clickhouse.getMappedPort(8123);
        httpUrl = "http://" + host + ":" + port + "/";

        // 1. Schema: analytics.esg_readings (matches V001).
        httpPost("CREATE DATABASE IF NOT EXISTS analytics");
        httpPost("""
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

        // 2. V032-equivalent provisioning: policy user + PERMISSIVE row policy.
        //    USING getSetting('SQL_tenant_id') — CH 23.8 removed currentSetting.
        //    AS PERMISSIVE — see class javadoc: RESTRICTIVE returns 0 rows in 23.8.
        httpPost("CREATE USER IF NOT EXISTS " + POLICY_USER
                + " IDENTIFIED WITH sha256_password BY '" + POLICY_PASSWORD + "'");
        httpPost("CREATE ROW POLICY IF NOT EXISTS tenant_iso_esg_readings "
                + "ON analytics.esg_readings FOR SELECT "
                + "USING tenant_id = getSetting('SQL_tenant_id') AS PERMISSIVE "
                + "TO " + POLICY_USER);
        httpPost("GRANT SELECT ON analytics.esg_readings TO " + POLICY_USER);

        // 3. Seed two tenants' worth of data via the privileged 'default' user.
        httpPost("INSERT INTO analytics.esg_readings "
                + "(tenant_id, building_id, source_id, metric_type, value, unit, recorded_at) VALUES "
                + "('tenant_A','BA','src','ENERGY',100.0,'kWh',fromUnixTimestamp(1000)),"
                + "('tenant_A','BA','src','ENERGY',200.0,'kWh',fromUnixTimestamp(1100)),"
                + "('tenant_B','BB','src','ENERGY',300.0,'kWh',fromUnixTimestamp(1200)),"
                + "('tenant_B','BB','src','ENERGY',400.0,'kWh',fromUnixTimestamp(1300))");

        // MergeTree flush
        Thread.sleep(500);

        policyJdbcUrl = "jdbc:clickhouse://" + host + ":" + port + "/analytics";
    }

    private void httpPost(String sql) throws Exception {
        URL url = new URL(httpUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(sql.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            String body = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException("ClickHouse HTTP " + code + " for SQL: "
                    + sql.substring(0, Math.min(80, sql.length())) + " — " + body);
        }
    }

    private DataSource policyDataSource() throws Exception {
        Properties props = new Properties();
        props.setProperty("user", POLICY_USER);
        props.setProperty("password", POLICY_PASSWORD);
        props.setProperty("compress", "0");
        // M5-1-T10: pin a session id so HTTP-mode JDBC connections carry the
        // SET SQL_tenant_id across to the subsequent SELECT. Without a
        // session_id the HTTP interface is stateless and SET is lost between
        // statements. See ClickHouseConfig.sessionId for production wiring.
        props.setProperty("session_id", "row-policy-it-" + System.currentTimeMillis());
        return new ClickHouseDataSource(policyJdbcUrl, props);
    }

    /**
     * Run a SELECT as a given tenant by setting the session setting on a
     * borrowed connection, mirroring what {@link RowPolicyEngine} does.
     */
    private int countRowsAs(String tenantId) throws Exception {
        try (Connection conn = policyDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            if (tenantId != null && !tenantId.isBlank()) {
                stmt.execute("SET SQL_tenant_id = '" + tenantId + "'");
            }
            // SQL OMITS WHERE tenant_id — RowPolicy (L2) must still restrict.
            try (var rs = stmt.executeQuery("SELECT count() FROM esg_readings")) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("tenant_A sees only its own 2 rows even without WHERE clause")
    void tenantA_seesOnlyOwnRows_evenWithoutWhereClause() throws Exception {
        assertEquals(2, countRowsAs("tenant_A"),
            "RowPolicy L2 must restrict SELECT to tenant_A rows (no WHERE needed)");
    }

    @Test
    @DisplayName("tenant_B sees only its own 2 rows")
    void tenantB_seesOnlyOwnRows() throws Exception {
        assertEquals(2, countRowsAs("tenant_B"),
            "RowPolicy L2 must restrict SELECT to tenant_B rows");
    }

    @Test
    @DisplayName("tenant_A and tenant_B counts never overlap (no cross-tenant leak)")
    void crossTenant_isolation_holds() throws Exception {
        int a = countRowsAs("tenant_A");
        int b = countRowsAs("tenant_B");
        // Seeded 2 rows per tenant; sum must equal 4 with no overlap.
        assertEquals(4, a + b, "tenant_A + tenant_B counts must sum to seeded total (no overlap)");
    }

    @Test
    @DisplayName("no SET SQL_tenant_id → SELECT errors (fail-closed, UNKNOWN_SETTING at policy eval)")
    void nullTenantSessionSetting_failsClosed() throws Exception {
        // Without SET, getSetting('SQL_tenant_id') throws Code 115 at policy
        // evaluation time → the SELECT errors out → no rows leak.
        try (Connection conn = policyDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () -> {
                try (var ignored = stmt.executeQuery("SELECT count() FROM esg_readings")) {
                    // not reached
                }
            });
            assertTrue(ex.getMessage().contains("UNKNOWN_SETTING")
                    || ex.getMessage().contains("SQL_tenant_id"),
                "Expected UNKNOWN_SETTING failure but got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("RowPolicyEngine SET/RESET does not bleed tenant across requests (Spike S1 §3.3)")
    void sessionSetting_doesNotBleedAcrossRequests() throws Exception {
        // Use the real RowPolicyEngine against the policy user's pool.
        JdbcTemplate jdbcTemplate = new JdbcTemplate(policyDataSource());
        RowPolicyEngine engine = new RowPolicyEngine(jdbcTemplate);

        // Request 1: query as tenant_A (SET then RESET in finally).
        Integer countA = engine.executeWithTenant("tenant_A", conn -> {
            try (var ps = conn.prepareStatement("SELECT count() FROM esg_readings");
                 var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertEquals(2, countA, "tenant_A scoped query must see its 2 rows");

        // Request 2: borrow a (possibly same) connection WITHOUT going through the
        // engine — the previous SET must NOT have leaked, so RowPolicy denies by
        // erroring (fail-closed).
        try (Connection conn = policyDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () -> {
                try (var ignored = stmt.executeQuery("SELECT count() FROM esg_readings")) { }
            });
            assertTrue(ex.getMessage().contains("UNKNOWN_SETTING"),
                "Previous SET must not leak — connection was reset by RowPolicyEngine (Spike S1 §3.3). "
                    + "Got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("RowPolicyEngine routes a real query through the scoped connection")
    void rowPolicyEngine_executesRealQueryOnScopedConnection() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(policyDataSource());
        RowPolicyEngine engine = new RowPolicyEngine(jdbcTemplate);

        // Same call shape ClickHouseEnergyRepository uses.
        java.util.List<String> buildings = engine.executeWithTenant("tenant_A", conn -> {
            java.util.List<String> out = new java.util.ArrayList<>();
            try (var ps = conn.prepareStatement(
                    "SELECT DISTINCT building_id FROM esg_readings ORDER BY building_id");
                 var rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        });

        assertThat(buildings).containsExactly("BA");
    }
}
