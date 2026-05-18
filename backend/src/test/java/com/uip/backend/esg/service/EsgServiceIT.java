package com.uip.backend.esg.service;

import com.uip.backend.esg.api.dto.EsgMetricDto;
import com.uip.backend.esg.api.dto.EsgReportDto;
import com.uip.backend.esg.api.dto.EsgSummaryDto;
import com.uip.backend.esg.config.CacheConfig;
import com.uip.backend.esg.config.analytics.AnalyticsPort;
import com.uip.backend.esg.config.analytics.EsgAggregateResult;
import com.uip.backend.esg.dto.EsgAnomalyDto;
import com.uip.backend.tenant.context.TenantContext;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.uip.backend.esg.service.EsgCacheWarmupService;

@SpringBootTest(properties = {
    "security.jwt.secret=test-secret-for-integration-tests-only-32chars",
    "spring.cache.type=none",
    "uip.cagg.alert-refresh-ms=999999999",
    "uip.cagg.sensor-refresh-ms=999999999"
})
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EsgServiceIT {

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
    @MockBean @SuppressWarnings("unused") AnalyticsPort analyticsPort;
    @MockBean @SuppressWarnings("unused") EsgReportGenerator reportGenerator;
    @MockBean @SuppressWarnings("unused") EsgCacheWarmupService cacheWarmupService;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EsgService esgService;
    @Autowired private PlatformTransactionManager txManager;

    private static final String TENANT_A = "esg-it-tenant-a";
    private static final String TENANT_B = "esg-it-tenant-b";
    private static final String BLD_A1 = "BLD-ESG-A1";
    private static final String BLD_A2 = "BLD-ESG-A2";
    private static final String BLD_B1 = "BLD-ESG-B1";

    @BeforeAll
    void setupData() {
        // Q1 2026 timestamps for getSummary tests (quarterRange/annualRange queries)
        Instant q1Mid    = Instant.parse("2026-02-15T10:00:00Z");
        Instant q1Early  = Instant.parse("2026-01-20T08:00:00Z");
        Instant q1Late   = Instant.parse("2026-03-20T14:00:00Z");

        // Recent timestamps for getEnergyData / getCarbonData / anomaly tests
        Instant now       = Instant.now();
        Instant hourAgo   = now.minus(1, ChronoUnit.HOURS);
        Instant dayAgo    = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);

        // Tenant A data
        txTemplate.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL app.tenant_id = '" + TENANT_A + "'");

            // ENERGY metrics (recent — for getEnergyData / anomaly tests)
            insertMetric(1, hourAgo, TENANT_A, "SRC-E-A1", "ENERGY", 150.0, "kWh", BLD_A1, "D01");
            insertMetric(2, dayAgo, TENANT_A, "SRC-E-A2", "ENERGY", 200.0, "kWh", BLD_A2, "D01");
            insertMetric(3, twoDaysAgo, TENANT_A, "SRC-E-A1", "ENERGY", 100.0, "kWh", BLD_A1, "D01");

            // CARBON metrics (Q1 2026 — for getSummary quarterly; recent for getCarbonData)
            insertMetric(4, q1Mid, TENANT_A, "SRC-C-A1", "CARBON", 50.0, "tCO2e", BLD_A1, "D01");
            insertMetric(5, q1Early, TENANT_A, "SRC-C-A2", "CARBON", 80.0, "tCO2e", BLD_A2, "D01");
            insertMetric(14, hourAgo, TENANT_A, "SRC-C-A1R", "CARBON", 45.0, "tCO2e", BLD_A1, "D01");
            insertMetric(15, dayAgo, TENANT_A, "SRC-C-A2R", "CARBON", 75.0, "tCO2e", BLD_A2, "D01");

            // WATER metrics (Q1 2026 — for getSummary quarterly assertions)
            insertMetric(6, q1Late, TENANT_A, "SRC-W-A1", "WATER", 30.0, "m3", BLD_A1, "D01");

            // Historical data for anomaly detection (older than 30 days)
            insertMetric(20, now.minus(31, ChronoUnit.DAYS), TENANT_A, "SRC-E-A1", "ENERGY", 50.0, "kWh", BLD_A1, "D01");
            insertMetric(21, now.minus(60, ChronoUnit.DAYS), TENANT_A, "SRC-E-A1", "ENERGY", 50.0, "kWh", BLD_A1, "D01");
        });

        // Tenant B data
        txTemplate.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL app.tenant_id = '" + TENANT_B + "'");

            // ENERGY metrics (recent)
            insertMetric(10, hourAgo, TENANT_B, "SRC-E-B1", "ENERGY", 300.0, "kWh", BLD_B1, "D02");
            insertMetric(11, dayAgo, TENANT_B, "SRC-E-B1", "ENERGY", 250.0, "kWh", BLD_B1, "D02");

            // ENERGY in Q1 2026 (for getSummary)
            insertMetric(12, q1Mid, TENANT_B, "SRC-E-B1", "ENERGY", 400.0, "kWh", BLD_B1, "D02");
            insertMetric(13, q1Early, TENANT_B, "SRC-E-B1", "ENERGY", 150.0, "kWh", BLD_B1, "D02");
        });

        // Stub AnalyticsPort — return tenant-specific results based on first arg
        when(analyticsPort.queryEnergyAggregate(anyString(), any(), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    String tenantId = invocation.getArgument(0);
                    if (TENANT_A.equals(tenantId)) {
                        return new EsgAggregateResult(450.0, 0.95, Map.of(), List.of());
                    } else if (TENANT_B.equals(tenantId)) {
                        return new EsgAggregateResult(550.0, 0.88, Map.of(), List.of());
                    }
                    return new EsgAggregateResult(0.0, 0.0, Map.of(), List.of());
                });
    }

    @BeforeEach
    void ensureMockStub() {
        // Re-stub in case Spring context was cached/restarted and mock lost its answers
        when(analyticsPort.queryEnergyAggregate(anyString(), any(), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    String tenantId = invocation.getArgument(0);
                    if (TENANT_A.equals(tenantId)) {
                        return new EsgAggregateResult(450.0, 0.95, Map.of(), List.of());
                    } else if (TENANT_B.equals(tenantId)) {
                        return new EsgAggregateResult(550.0, 0.88, Map.of(), List.of());
                    }
                    return new EsgAggregateResult(0.0, 0.0, Map.of(), List.of());
                });
    }

    private void insertMetric(long id, Instant ts, String tenantId, String sourceId,
                              String metricType, double value, String unit,
                              String buildingId, String districtCode) {
        jdbc.update("""
            INSERT INTO esg.clean_metrics (id, timestamp, tenant_id, source_id, metric_type, value, unit, building_id, district_code)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, Timestamp.from(ts), tenantId, sourceId, metricType, value, unit, buildingId, districtCode);
    }

    // ─── getSummary ─────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void getSummary_quarterly_returnsEnergyFromAnalyticsPort() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            EsgSummaryDto summary = esgService.getSummary(TENANT_A, "QUARTERLY", 2026, 1);
            // ENERGY comes from mocked AnalyticsPort
            assertThat(summary.getTotalEnergyKwh()).isEqualTo(450.0);
            // CARBON and WATER from raw DB (Q1 2026 range)
            assertThat(summary.getTotalCarbonTco2e()).isEqualTo(130.0); // 50.0 + 80.0
            assertThat(summary.getTotalWaterM3()).isEqualTo(30.0);
            assertThat(summary.getPeriod()).isEqualTo("QUARTERLY");
            assertThat(summary.getYear()).isEqualTo(2026);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(2)
    void getSummary_annual_returnsCorrectPeriod() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            EsgSummaryDto summary = esgService.getSummary(TENANT_A, "ANNUAL", 2026, 0);
            assertThat(summary).as("getSummary ANNUAL should not return null").isNotNull();
            assertThat(summary.getPeriod()).isEqualTo("ANNUAL");
            assertThat(summary.getQuarter()).isEqualTo(0);
        } catch (Throwable e) {
            System.out.println("===DEBUG getSummary_annual: " + e.getClass().getName() + ": " + e.getMessage());
            for (StackTraceElement ste : e.getStackTrace()) {
                System.out.println("  at " + ste);
                if (ste.getClassName().contains("uip.backend")) break;
            }
            if (e.getCause() != null) {
                System.out.println("  Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            throw e;
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(3)
    void getSummary_tenantIsolation_bCannotSeeA() {
        TenantContext.setCurrentTenant(TENANT_B);
        try {
            EsgSummaryDto summary = esgService.getSummary(TENANT_B, "QUARTERLY", 2026, 1);
            assertThat(summary).as("getSummary TENANT_B should not return null").isNotNull();
            assertThat(summary.getTotalEnergyKwh()).isEqualTo(550.0);
            assertThat(summary.getTotalWaterM3()).isNull();
        } catch (AssertionError | Exception e) {
            System.err.println("DEBUG getSummary_tenantIsolation: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        } finally {
            TenantContext.clear();
        }
    }

    // ─── getEnergyData ─────────────────────────────────────────────────────

    @Test
    @Order(10)
    void getEnergyData_all_returnsAllEnergyMetrics() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Instant from = Instant.now().minus(3, ChronoUnit.DAYS);
            List<EsgMetricDto> data = esgService.getEnergyData(TENANT_A, from, Instant.now(), null);
            assertThat(data).hasSize(3);
            assertThat(data).allSatisfy(d -> assertThat(d.getMetricType()).isEqualTo("ENERGY"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(11)
    void getEnergyData_filteredByBuilding_returnsOnlyBuildingMetrics() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Instant from = Instant.now().minus(3, ChronoUnit.DAYS);
            List<EsgMetricDto> data = esgService.getEnergyData(TENANT_A, from, Instant.now(), BLD_A1);
            assertThat(data).hasSize(2);
            assertThat(data).allSatisfy(d -> assertThat(d.getBuildingId()).isEqualTo(BLD_A1));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(12)
    void getEnergyData_defaultRange_usedWhenNullProvided() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            List<EsgMetricDto> data = esgService.getEnergyData(TENANT_A, null, null, null);
            assertThat(data).isNotEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(13)
    void getEnergyData_tenantIsolation() {
        TenantContext.setCurrentTenant(TENANT_B);
        try {
            Instant from = Instant.now().minus(3, ChronoUnit.DAYS);
            List<EsgMetricDto> data = esgService.getEnergyData(TENANT_B, from, Instant.now(), null);
            assertThat(data).hasSize(2);
            assertThat(data).allSatisfy(d -> assertThat(d.getMetricType()).isEqualTo("ENERGY"));
        } finally {
            TenantContext.clear();
        }
    }

    // ─── getCarbonData ─────────────────────────────────────────────────────

    @Test
    @Order(20)
    void getCarbonData_returnsOnlyCarbonMetrics() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Instant from = Instant.now().minus(3, ChronoUnit.DAYS);
            List<EsgMetricDto> data = esgService.getCarbonData(TENANT_A, from, Instant.now());
            assertThat(data).hasSize(2);
            assertThat(data).allSatisfy(d -> assertThat(d.getMetricType()).isEqualTo("CARBON"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(21)
    void getCarbonData_noDataForTenant_returnsEmpty() {
        TenantContext.setCurrentTenant(TENANT_B);
        try {
            Instant from = Instant.now().minus(3, ChronoUnit.DAYS);
            List<EsgMetricDto> data = esgService.getCarbonData(TENANT_B, from, Instant.now());
            assertThat(data).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    // ─── triggerReportGeneration ───────────────────────────────────────────

    @Test
    @Order(30)
    void triggerReport_returnsPendingReport() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            EsgReportDto report = esgService.triggerReportGeneration(TENANT_A, "QUARTERLY", 2026, 1);
            assertThat(report.getStatus()).isEqualTo("PENDING");
            assertThat(report.getPeriodType()).isEqualTo("QUARTERLY");
            assertThat(report.getYear()).isEqualTo(2026);
            assertThat(report.getQuarter()).isEqualTo(1);
            assertThat(report.getDownloadUrl()).isNull();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(31)
    void getReportStatus_afterCreation_returnsPending() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            EsgReportDto created = esgService.triggerReportGeneration(TENANT_A, "ANNUAL", 2025, 0);
            EsgReportDto status = esgService.getReportStatus(TENANT_A, created.getId());
            assertThat(status.getStatus()).isEqualTo("PENDING");
            assertThat(status.getId()).isEqualTo(created.getId());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(32)
    void getReportStatus_notFound_throwsException() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> esgService.getReportStatus(TENANT_A, java.util.UUID.randomUUID()))
                    .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        } finally {
            TenantContext.clear();
        }
    }

    // ─── anomaly detection ─────────────────────────────────────────────────

    @Test
    @Order(40)
    void detectUtilityAnomalies_noAnomaly_whenUsageNormal() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            List<EsgAnomalyDto> anomalies = esgService.detectUtilityAnomalies(TENANT_A);
            // Current energy: ~250 in last 30d, historical: ~50 in prev 30d
            // 250 / 50 = 5.0 > 1.3 threshold → anomaly detected
            assertThat(anomalies).isNotEmpty();
            assertThat(anomalies.get(0).metricType()).isEqualTo("energy");
            assertThat(anomalies.get(0).currentValue()).isGreaterThan(0);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(41)
    void detectEsgAnomalies_includesAllTypes() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            List<EsgAnomalyDto> anomalies = esgService.detectEsgAnomalies(TENANT_A);
            assertThat(anomalies).isNotEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    // ─── time range filtering ──────────────────────────────────────────────

    @Test
    @Order(50)
    void getEnergyData_narrowRange_returnsFewerResults() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Instant from = Instant.now().minus(2, ChronoUnit.HOURS);
            List<EsgMetricDto> data = esgService.getEnergyData(TENANT_A, from, Instant.now(), null);
            assertThat(data).hasSize(1);
            assertThat(data.get(0).getSourceId()).isEqualTo("SRC-E-A1");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(51)
    void getEnergyData_noMatch_returnsEmpty() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Instant from = Instant.now().minus(1, ChronoUnit.MINUTES);
            List<EsgMetricDto> data = esgService.getEnergyData(TENANT_A, from, Instant.now(), null);
            assertThat(data).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(52)
    void getEnergyData_nonExistentBuilding_returnsEmpty() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Instant from = Instant.now().minus(3, ChronoUnit.DAYS);
            List<EsgMetricDto> data = esgService.getEnergyData(TENANT_A, from, Instant.now(), "BLD-NONEXISTENT");
            assertThat(data).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(53)
    void getEnergyData_dtoFields_correctlyMapped() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Instant from = Instant.now().minus(2, ChronoUnit.HOURS);
            List<EsgMetricDto> data = esgService.getEnergyData(TENANT_A, from, Instant.now(), null);
            assertThat(data).hasSize(1);
            EsgMetricDto dto = data.get(0);
            assertThat(dto.getValue()).isEqualTo(150.0);
            assertThat(dto.getUnit()).isEqualTo("kWh");
            assertThat(dto.getBuildingId()).isEqualTo(BLD_A1);
            assertThat(dto.getSourceId()).isEqualTo("SRC-E-A1");
        } finally {
            TenantContext.clear();
        }
    }

}
