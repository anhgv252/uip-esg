package com.uip.backend.building.service;

import com.uip.backend.building.api.dto.CrossBuildingAggregationRequest;
import com.uip.backend.building.api.dto.CrossBuildingAggregationResult;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HB-EXT-07: RLS-010 Java 50-concurrent integration test.
 *
 * Verifies zero cross-tenant contamination when 50 threads (25 per tenant)
 * call CrossBuildingAggregationService.aggregate() simultaneously.
 * Tests thread-safety of TenantContext (ThreadLocal) + JdbcTemplate
 * connection pool reuse under concurrent load.
 *
 * Follows TenantIsolationIT pattern: Testcontainers PG, @MockBean infra.
 */
@SpringBootTest(properties = "security.jwt.secret=test-secret-for-integration-tests-only-32chars")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrossBuildingConcurrentRLSIT {

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

    private static final String TENANT_A = "tenant-concurrent-a";
    private static final String TENANT_B = "tenant-concurrent-b";
    private static final List<String> BUILDINGS_A = List.of("BLD-CA-001", "BLD-CA-002", "BLD-CA-003");
    private static final List<String> BUILDINGS_B = List.of("BLD-CB-001", "BLD-CB-002", "BLD-CB-003");
    private static final OffsetDateTime FROM = OffsetDateTime.now().minusDays(7);
    private static final OffsetDateTime TO = OffsetDateTime.now();

    @BeforeAll
    void seedTestData() {
        jdbc.execute("INSERT INTO public.tenants (tenant_id, tenant_name, is_aggregator) " +
                "VALUES ('" + TENANT_A + "', 'Concurrent Test A', false) ON CONFLICT (tenant_id) DO NOTHING");
        jdbc.execute("INSERT INTO public.tenants (tenant_id, tenant_name, is_aggregator) " +
                "VALUES ('" + TENANT_B + "', 'Concurrent Test B', false) ON CONFLICT (tenant_id) DO NOTHING");

        for (int i = 0; i < BUILDINGS_A.size(); i++) {
            jdbc.execute("INSERT INTO public.buildings (building_code, building_name, tenant_id, cluster_id, floor_count, total_area_m2) " +
                    "VALUES ('" + BUILDINGS_A.get(i) + "', 'Tenant A Building " + (i+1) + "', '" + TENANT_A + "', 'cluster-ca', 10, 5000.0) " +
                    "ON CONFLICT (tenant_id, building_code) DO NOTHING");
        }
        for (int i = 0; i < BUILDINGS_B.size(); i++) {
            jdbc.execute("INSERT INTO public.buildings (building_code, building_name, tenant_id, cluster_id, floor_count, total_area_m2) " +
                    "VALUES ('" + BUILDINGS_B.get(i) + "', 'Tenant B Building " + (i+1) + "', '" + TENANT_B + "', 'cluster-cb', 15, 8000.0) " +
                    "ON CONFLICT (tenant_id, building_code) DO NOTHING");
        }

        for (String bc : BUILDINGS_A) {
            for (int j = 0; j < 100; j++) {
                jdbc.execute("INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, tenant_id) " +
                        "VALUES ('src-ca-" + bc + "', 'ENERGY', NOW() - interval '" + j + " minutes', " +
                        (50.0 + j * 0.5) + ", 'kWh', '" + bc + "', '" + TENANT_A + "')");
            }
        }
        for (String bc : BUILDINGS_B) {
            for (int j = 0; j < 100; j++) {
                jdbc.execute("INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, tenant_id) " +
                        "VALUES ('src-cb-" + bc + "', 'ENERGY', NOW() - interval '" + j + " minutes', " +
                        (80.0 + j * 0.3) + ", 'kWh', '" + bc + "', '" + TENANT_B + "')");
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("RLS-010: 50 concurrent requests — zero cross-tenant contamination")
    void fiftyConcurrentRequests_zeroCrossTenantContamination() throws Exception {
        for (int iteration = 1; iteration <= 5; iteration++) {
            ExecutorService executor = Executors.newFixedThreadPool(50);
            List<Future<List<CrossBuildingAggregationResult>>> futures = new ArrayList<>();

            try {
                for (int i = 0; i < 25; i++) {
                    futures.add(executor.submit(() -> queryForTenant(TENANT_A, BUILDINGS_A)));
                    futures.add(executor.submit(() -> queryForTenant(TENANT_B, BUILDINGS_B)));
                }

                // futures alternate: even index = Tenant A, odd index = Tenant B
                for (int idx = 0; idx < futures.size(); idx++) {
                    List<CrossBuildingAggregationResult> results = futures.get(idx).get(30, TimeUnit.SECONDS);
                    List<String> expectedBuildings = (idx % 2 == 0) ? BUILDINGS_A : BUILDINGS_B;
                    String tenant = (idx % 2 == 0) ? TENANT_A : TENANT_B;
                    for (CrossBuildingAggregationResult r : results) {
                        assertThat(expectedBuildings)
                                .as("Tenant contamination: building %s returned for %s but belongs to other tenant",
                                        r.buildingCode(), tenant)
                                .contains(r.buildingCode());
                    }
                }
            } finally {
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("RLS-010: Sequential alternating queries — no connection leak")
    void sequentialAlternatingQueries_noConnectionLeak() {
        for (int i = 0; i < 20; i++) {
            TenantContext.setCurrentTenant(TENANT_A);
            try {
                var results = aggregationService.aggregate(TENANT_A,
                        new CrossBuildingAggregationRequest(BUILDINGS_A, "ENERGY", FROM, TO));
                for (var r : results) {
                    assertThat(BUILDINGS_A).contains(r.buildingCode());
                }
            } finally {
                TenantContext.clear();
            }

            TenantContext.setCurrentTenant(TENANT_B);
            try {
                var results = aggregationService.aggregate(TENANT_B,
                        new CrossBuildingAggregationRequest(BUILDINGS_B, "ENERGY", FROM, TO));
                for (var r : results) {
                    assertThat(BUILDINGS_B).contains(r.buildingCode());
                }
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("RLS-010: Tenant A requesting tenant B buildings returns empty")
    void tenantARequestingTenantBBuildings_returnsEmpty() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            var results = aggregationService.aggregate(TENANT_A,
                    new CrossBuildingAggregationRequest(BUILDINGS_B, "ENERGY", FROM, TO));
            assertThat(results).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    private List<CrossBuildingAggregationResult> queryForTenant(String tenantId, List<String> buildingCodes) {
        TenantContext.setCurrentTenant(tenantId);
        try {
            return aggregationService.aggregate(tenantId,
                    new CrossBuildingAggregationRequest(buildingCodes, "ENERGY", FROM, TO));
        } finally {
            TenantContext.clear();
        }
    }
}
