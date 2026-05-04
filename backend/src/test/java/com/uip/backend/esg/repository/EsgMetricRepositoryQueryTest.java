package com.uip.backend.esg.repository;

import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.domain.EsgMetricId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link EsgMetricRepository} JPQL queries.
 * <p>
 * Verifies that all three queries ({@code findByTypeAndRange}, {@code findByTypeAndBuilding},
 * {@code sumByTypeAndRange}) correctly filter by {@code tenantId}, enforcing tenant isolation at the
 * repository layer (BT-22b).
 * <p>
 * Skipped automatically when Docker is unavailable via {@code @Testcontainers(disabledWithoutDocker=true)}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
@DisplayName("EsgMetricRepository — JPQL tenant isolation")
class EsgMetricRepositoryQueryTest {

    private static final AtomicLong ID_SEQ = new AtomicLong(System.currentTimeMillis());

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("uip")
            .withPassword("test_password");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6399");
        registry.add("spring.data.redis.password", () -> "testredis");
        registry.add("security.jwt.secret",
                () -> java.util.Base64.getEncoder().encodeToString(
                        "uip-integration-test-secret-32b!".getBytes()));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    // ─── Repository under test ───────────────────────────────────────────────

    @Autowired
    EsgMetricRepository repository;

    // ─── Tests ───────────────────────────────────────────────────────────────

    private static final String TENANT_A = "hcm";
    private static final String TENANT_B = "hanoi";

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T01:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-01T02:00:00Z");

    @Test
    @DisplayName("findByTypeAndRange — returns only rows matching tenantId")
    void findByTypeAndRange_isolatesByTenantId() {
        repository.save(metric(TENANT_A, "ENERGY", T1, 100.0, null));
        repository.save(metric(TENANT_A, "ENERGY", T2, 200.0, null));
        repository.save(metric(TENANT_B, "ENERGY", T1, 999.0, null));

        List<EsgMetric> hcmResults = repository.findByTypeAndRange(TENANT_A, "ENERGY", T1, T3);
        List<EsgMetric> hanoiResults = repository.findByTypeAndRange(TENANT_B, "ENERGY", T1, T3);

        assertThat(hcmResults).hasSize(2);
        assertThat(hcmResults).allMatch(m -> TENANT_A.equals(m.getTenantId()));

        assertThat(hanoiResults).hasSize(1);
        assertThat(hanoiResults).allMatch(m -> TENANT_B.equals(m.getTenantId()));
    }

    @Test
    @DisplayName("findByTypeAndBuilding — returns only rows matching tenantId AND buildingId")
    void findByTypeAndBuilding_isolatesByTenantIdAndBuilding() {
        repository.save(metric(TENANT_A, "ENERGY", T1, 150.0, "B1"));
        repository.save(metric(TENANT_B, "ENERGY", T1, 888.0, "B1"));

        List<EsgMetric> results = repository.findByTypeAndBuilding(TENANT_A, "ENERGY", "B1", T1, T3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTenantId()).isEqualTo(TENANT_A);
        assertThat(results.get(0).getValue()).isEqualTo(150.0);
    }

    @Test
    @DisplayName("sumByTypeAndRange — sums only matching tenant rows")
    void sumByTypeAndRange_aggregatesOnlyOwnTenantRows() {
        repository.save(metric(TENANT_A, "CARBON", T1, 50.0, null));
        repository.save(metric(TENANT_A, "CARBON", T2, 30.0, null));
        repository.save(metric(TENANT_B, "CARBON", T1, 1000.0, null));

        Double hcmSum = repository.sumByTypeAndRange(TENANT_A, "CARBON", T1, T3);
        Double hanoiSum = repository.sumByTypeAndRange(TENANT_B, "CARBON", T1, T3);

        assertThat(hcmSum).isEqualTo(80.0);
        assertThat(hanoiSum).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("findByTypeAndRange — returns empty list when no rows for tenant")
    void findByTypeAndRange_noRowsForTenant_returnsEmpty() {
        List<EsgMetric> results = repository.findByTypeAndRange("nonexistent-tenant", "ENERGY", T1, T3);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("sumByTypeAndRange — returns null when no rows for tenant")
    void sumByTypeAndRange_noRowsForTenant_returnsNull() {
        Double sum = repository.sumByTypeAndRange("nonexistent-tenant", "WATER", T1, T3);
        assertThat(sum).isNull();
    }

    // ─── Builder ────────────────────────────────────────────────────────────

    private EsgMetric metric(String tenantId, String type, Instant timestamp, Double value, String buildingId) {
        EsgMetric m = new EsgMetric();
        m.setId(new EsgMetricId(ID_SEQ.getAndIncrement(), timestamp));
        m.setTenantId(tenantId);
        m.setMetricType(type);
        m.setSourceId("TEST-SENSOR-" + ID_SEQ.get());
        m.setValue(value);
        m.setUnit("kWh");
        m.setBuildingId(buildingId);
        return m;
    }
}
