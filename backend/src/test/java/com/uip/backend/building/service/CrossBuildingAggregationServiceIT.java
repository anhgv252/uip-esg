package com.uip.backend.building.service;

import com.uip.backend.building.api.dto.CrossBuildingAggregationRequest;
import com.uip.backend.building.api.dto.CrossBuildingAggregationResult;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * HB-EXT-06: CrossBuildingAggregationService integration test — ≥85% coverage.
 *
 * Tests against a real PostgreSQL (Testcontainers) with Flyway migrations applied.
 * Verifies: correct aggregation math, cross-tenant isolation, time range filtering,
 * metric type filtering, inactive building exclusion, and ≤500ms performance.
 *
 * Follows TenantIsolationIT / CrossBuildingConcurrentRLSIT patterns.
 */
@SpringBootTest(properties = "security.jwt.secret=test-secret-for-integration-tests-only-32chars")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrossBuildingAggregationServiceIT {

    @Container
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
        registry.add("uip.capabilities.multi-tenancy", () -> "true");
    }

    @MockBean @SuppressWarnings("unused") RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused") ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused") StringRedisTemplate redisTemplate;
    @MockBean @SuppressWarnings("unused") RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused") KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CrossBuildingAggregationService aggregationService;

    private static final String TENANT_A    = "tenant-agg-a";
    private static final String TENANT_B    = "tenant-agg-b";
    private static final String BLD_A1      = "BLD-AGG-A1";
    private static final String BLD_A2      = "BLD-AGG-A2";
    private static final String BLD_A3      = "BLD-AGG-A3";
    private static final String BLD_B1      = "BLD-AGG-B1";
    private static final String BLD_INACTIVE = "BLD-AGG-INACTIVE";

    // Truncate to minutes for deterministic BETWEEN assertions
    private static final OffsetDateTime BASE = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);

    @BeforeAll
    void seedTestData() {
        seedTenant(TENANT_A, "Agg Test A", true);
        seedTenant(TENANT_B, "Agg Test B", false);

        seedBuilding(BLD_A1,       "Tower A1",       TENANT_A, true);
        seedBuilding(BLD_A2,       "Tower A2",       TENANT_A, true);
        seedBuilding(BLD_A3,       "Tower A3",       TENANT_A, true);
        seedBuilding(BLD_B1,       "Tower B1",       TENANT_B, true);
        seedBuilding(BLD_INACTIVE, "Inactive Bldg",  TENANT_A, false);

        // A1: 500 ENERGY rows at 1-minute intervals, value=100.0 each
        // Covers BASE-500min to BASE-1min — all within 7-day window
        for (int i = 1; i <= 500; i++) {
            insertMetric("src-a1", "ENERGY", BASE.minusMinutes(i), 100.0, "kWh", BLD_A1, TENANT_A);
        }

        // A2: 50 ENERGY rows, value=200.0
        for (int i = 1; i <= 50; i++) {
            insertMetric("src-a2", "ENERGY", BASE.minusMinutes(i), 200.0, "kWh", BLD_A2, TENANT_A);
        }

        // A3: WATER only — no ENERGY data
        insertMetric("src-a3", "WATER", BASE.minusMinutes(1), 50.0, "m3", BLD_A3, TENANT_A);

        // B1: different tenant — must not appear in TENANT_A queries
        insertMetric("src-b1", "ENERGY", BASE.minusMinutes(1), 999.0, "kWh", BLD_B1, TENANT_B);

        // A1: one out-of-range row 30 days ago (must be excluded from 7-day window)
        insertMetric("src-a1", "ENERGY", BASE.minusDays(30), 5000.0, "kWh", BLD_A1, TENANT_A);
    }

    @Test
    @Order(1)
    @DisplayName("AGG-IT-01: Single building — SUM, AVG, COUNT correct for 500 rows")
    void singleBuilding_aggregation_correctStats() {
        var results = queryA1(BASE.minusDays(7), BASE.plusMinutes(1));

        assertThat(results).hasSize(1);
        CrossBuildingAggregationResult r = results.get(0);
        assertThat(r.buildingCode()).isEqualTo(BLD_A1);
        assertThat(r.buildingName()).isEqualTo("Tower A1");
        assertThat(r.dataPoints()).isEqualTo(500L);
        assertThat(r.totalValue()).isCloseTo(500 * 100.0, offset(0.001));
        assertThat(r.avgValue()).isCloseTo(100.0, offset(0.001));
        assertThat(r.unit()).isEqualTo("kWh");
    }

    @Test
    @Order(2)
    @DisplayName("AGG-IT-02: Cross-tenant isolation — Tenant A gets empty for Tenant B building")
    void crossTenant_tenantARequestsTenantBBuilding_returnsEmpty() {
        var req = new CrossBuildingAggregationRequest(
                List.of(BLD_B1), "ENERGY",
                BASE.minusDays(7), BASE.plusMinutes(1));

        // BLD_B1 belongs to TENANT_B — not in buildingRepository for TENANT_A
        var results = aggregationService.aggregate(TENANT_A, req);

        assertThat(results).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("AGG-IT-03: Time range filter — only rows within [BASE-150s, BASE] counted")
    void timeRange_narrowWindow_countsTwoRowsOnly() {
        // Window covers exactly 2 rows: BASE-1min and BASE-2min
        OffsetDateTime from = BASE.minusSeconds(150); // 2.5 min ago
        OffsetDateTime to   = BASE.plusMinutes(1);

        var req = new CrossBuildingAggregationRequest(List.of(BLD_A1), "ENERGY", from, to);
        var results = aggregationService.aggregate(TENANT_A, req);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).dataPoints()).isEqualTo(2L);
        assertThat(results.get(0).totalValue()).isCloseTo(200.0, offset(0.001));
    }

    @Test
    @Order(4)
    @DisplayName("AGG-IT-04: Out-of-range row (30d ago, value=5000) excluded from SUM")
    void outOfRange_row30DaysAgo_excluded() {
        // 7-day window should include 500 rows (value=100 each) but NOT the 30d-ago row (value=5000)
        var results = queryA1(BASE.minusDays(7), BASE.plusMinutes(1));

        assertThat(results.get(0).dataPoints()).isEqualTo(500L);
        assertThat(results.get(0).totalValue()).isCloseTo(500 * 100.0, offset(0.001));
    }

    @Test
    @Order(5)
    @DisplayName("AGG-IT-05: Metric type filter — ENERGY query on WATER-only building returns empty")
    void metricTypeFilter_wrongType_returnsEmpty() {
        // A3 is active and belongs to TENANT_A — it enters buildingMap
        // But has only WATER data — SQL returns no rows for ENERGY
        var req = new CrossBuildingAggregationRequest(
                List.of(BLD_A3), "ENERGY",
                BASE.minusDays(7), BASE.plusMinutes(1));

        var results = aggregationService.aggregate(TENANT_A, req);

        assertThat(results).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("AGG-IT-06: Multi-building — A1 and A2 aggregated independently and correctly")
    void twoBuildings_aggregatedSeparately() {
        var req = new CrossBuildingAggregationRequest(
                List.of(BLD_A1, BLD_A2), "ENERGY",
                BASE.minusDays(7), BASE.plusMinutes(1));

        var results = aggregationService.aggregate(TENANT_A, req);

        assertThat(results).hasSize(2);
        CrossBuildingAggregationResult a1 = findByCode(results, BLD_A1);
        CrossBuildingAggregationResult a2 = findByCode(results, BLD_A2);

        assertThat(a1.dataPoints()).isEqualTo(500L);
        assertThat(a1.buildingName()).isEqualTo("Tower A1");

        assertThat(a2.dataPoints()).isEqualTo(50L);
        assertThat(a2.buildingName()).isEqualTo("Tower A2");
        assertThat(a2.totalValue()).isCloseTo(50 * 200.0, offset(0.001));
        assertThat(a2.avgValue()).isCloseTo(200.0, offset(0.001));
    }

    @Test
    @Order(7)
    @DisplayName("AGG-IT-07: Inactive building excluded — not in buildingMap, returns empty")
    void inactiveBuilding_notInBuildingMap_returnsEmpty() {
        var req = new CrossBuildingAggregationRequest(
                List.of(BLD_INACTIVE), "ENERGY",
                BASE.minusDays(7), BASE.plusMinutes(1));

        // findByTenantIdAndIsActiveTrue filters out BLD_INACTIVE → buildingMap empty → returns early
        var results = aggregationService.aggregate(TENANT_A, req);

        assertThat(results).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("AGG-IT-08: No active buildings for unknown tenant — returns empty without DB query")
    void unknownTenant_returnsEmpty() {
        var req = new CrossBuildingAggregationRequest(
                List.of(BLD_A1), "ENERGY",
                BASE.minusDays(7), BASE.plusMinutes(1));

        var results = aggregationService.aggregate("tenant-unknown-xyz", req);

        assertThat(results).isEmpty();
    }

    @Test
    @Order(9)
    @DisplayName("AGG-IT-09: WATER metric returns correct data for A3")
    void waterMetric_returnsCorrectData() {
        var req = new CrossBuildingAggregationRequest(
                List.of(BLD_A3), "WATER",
                BASE.minusDays(7), BASE.plusMinutes(1));

        var results = aggregationService.aggregate(TENANT_A, req);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).buildingCode()).isEqualTo(BLD_A3);
        assertThat(results.get(0).dataPoints()).isEqualTo(1L);
        assertThat(results.get(0).totalValue()).isCloseTo(50.0, offset(0.001));
        assertThat(results.get(0).unit()).isEqualTo("m3");
    }

    @Test
    @Order(10)
    @DisplayName("AGG-IT-10: Performance — 500-row aggregation completes in ≤500ms")
    void performance_500rows_completesIn500ms() {
        var req = new CrossBuildingAggregationRequest(
                List.of(BLD_A1), "ENERGY",
                BASE.minusDays(7), BASE.plusMinutes(1));

        long start = System.currentTimeMillis();
        var results = aggregationService.aggregate(TENANT_A, req);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(results).hasSize(1);
        assertThat(results.get(0).dataPoints()).isEqualTo(500L);
        assertThat(elapsed)
                .as("Query with 500 rows should complete in ≤500ms, took %dms", elapsed)
                .isLessThanOrEqualTo(500L);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private List<CrossBuildingAggregationResult> queryA1(OffsetDateTime from, OffsetDateTime to) {
        return aggregationService.aggregate(TENANT_A,
                new CrossBuildingAggregationRequest(List.of(BLD_A1), "ENERGY", from, to));
    }

    private CrossBuildingAggregationResult findByCode(
            List<CrossBuildingAggregationResult> results, String code) {
        return results.stream()
                .filter(r -> r.buildingCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Building not found in results: " + code));
    }

    private void seedTenant(String id, String name, boolean isAggregator) {
        jdbc.execute("INSERT INTO public.tenants (tenant_id, tenant_name, is_aggregator) " +
                "VALUES ('" + id + "','" + name + "'," + isAggregator + ") " +
                "ON CONFLICT (tenant_id) DO NOTHING");
    }

    private void seedBuilding(String code, String name, String tenantId, boolean active) {
        jdbc.execute("INSERT INTO public.buildings (building_code, building_name, tenant_id, " +
                "cluster_id, floor_count, total_area_m2, is_active) " +
                "VALUES ('" + code + "','" + name + "','" + tenantId + "'," +
                "'cluster-agg',10,5000.0," + active + ") " +
                "ON CONFLICT (tenant_id, building_code) DO NOTHING");
    }

    private void insertMetric(String sourceId, String metricType, OffsetDateTime ts,
                               double value, String unit, String buildingId, String tenantId) {
        jdbc.execute("INSERT INTO esg.clean_metrics " +
                "(source_id, metric_type, timestamp, value, unit, building_id, tenant_id) " +
                "VALUES ('" + sourceId + "','" + metricType + "','" + ts + "'," +
                value + ",'" + unit + "','" + buildingId + "','" + tenantId + "')");
    }
}
