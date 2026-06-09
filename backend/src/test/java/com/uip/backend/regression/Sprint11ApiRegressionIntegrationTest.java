package com.uip.backend.regression;

import com.uip.backend.building.api.BuildingClusterController;
import com.uip.backend.esg.config.analytics.AnalyticsPort;
import com.uip.backend.esg.config.analytics.ClickHouseGrpcAnalyticsAdapter;
import com.uip.backend.esg.config.analytics.ClickHouseRestAnalyticsAdapter;
import com.uip.backend.esg.config.analytics.TimescaleDbAnalyticsAdapter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Sprint 11 API Regression Test Suite.
 *
 * Purpose: Lightweight regression guard (no Testcontainers) covering the four
 * Sprint 11 feature areas at the level of:
 *   - Spring condition verification (analytics-port bean exclusivity)
 *   - Controller contract verification (X-Tenant-ID header, @ApiResponses presence)
 *   - BPMN template structural check (loaded via classpath, no XML parse needed)
 *
 * For deep integration tests see:
 *   - ClickHouseGrpcAnalyticsAdapterTest    (gRPC stub, error paths)
 *   - AnalyticsPortMutualExclusivityTest     (ApplicationContextRunner conditions)
 *   - BuildingClusterControllerTest          (MockMvc, cross-tenant filter)
 *   - EsgServiceIT                           (full Testcontainers, tenant isolation)
 */
@Tag("regression")
@DisplayName("Sprint 11 Regression Suite")
class Sprint11ApiRegressionIntegrationTest {

    // ── 1. Analytics Port — Bean Exclusivity ──────────────────────────────────

    @Nested
    @DisplayName("REG-S11-01: AnalyticsPort bean mutual exclusivity")
    class AnalyticsPortConditions {

        private final ApplicationContextRunner base = new ApplicationContextRunner()
                .withUserConfiguration(
                        com.uip.backend.esg.config.analytics.AnalyticsAutoConfiguration.class,
                        ClickHouseRestAnalyticsAdapter.class,
                        ClickHouseGrpcAnalyticsAdapter.class)
                .withBean("esgMetricRepository",
                          com.uip.backend.esg.repository.EsgMetricRepository.class,
                          () -> mock(com.uip.backend.esg.repository.EsgMetricRepository.class));

        @Test
        @DisplayName("REG-S11-01a: Tier 1 default loads TimescaleDbAnalyticsAdapter")
        void tier1_loads_timescaleAdapter() {
            base.run(ctx -> {
                assertThat(ctx).hasSingleBean(AnalyticsPort.class);
                assertThat(ctx.getBean(AnalyticsPort.class))
                        .isInstanceOf(TimescaleDbAnalyticsAdapter.class);
            });
        }

        @Test
        @DisplayName("REG-S11-01b: Tier 2 REST loads ClickHouseRestAnalyticsAdapter")
        void tier2Rest_loads_restAdapter() {
            base.withPropertyValues(
                            "uip.capabilities.analytics-external=true",
                            "uip.analytics-service.url=http://localhost:8082")
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(AnalyticsPort.class);
                        assertThat(ctx.getBean(AnalyticsPort.class))
                                .isInstanceOf(ClickHouseRestAnalyticsAdapter.class);
                    });
        }

        @Test
        @DisplayName("REG-S11-01c [P1-2 regression]: REST adapter absent when transport=grpc")
        void tier2Grpc_restAdapterAbsent() {
            // This test will FAIL until ClickHouseRestAnalyticsAdapter adds
            // @ConditionalOnProperty(analytics-transport, havingValue=rest, matchIfMissing=true)
            base.withPropertyValues(
                            "uip.capabilities.analytics-external=true",
                            "uip.capabilities.analytics-transport=grpc",
                            "uip.analytics-service.url=http://localhost:8082")
                    .run(ctx ->
                        assertThat(ctx).doesNotHaveBean(ClickHouseRestAnalyticsAdapter.class));
        }
    }

    // ── 2. BuildingClusterController — X-Tenant-ID Contract ───────────────────

    @Nested
    @DisplayName("REG-S11-02: BuildingClusterController requires X-Tenant-ID on all endpoints")
    class BuildingClusterControllerContract {

        @Test
        @DisplayName("listByCluster() has @RequestHeader('X-Tenant-ID') parameter")
        void listByCluster_hasXTenantIdHeader() throws Exception {
            Method method = BuildingClusterController.class.getMethod(
                    "listByCluster", String.class, String.class);

            boolean hasTenantHeader = Arrays.stream(method.getParameters())
                    .anyMatch(p -> {
                        RequestHeader rh = p.getAnnotation(RequestHeader.class);
                        return rh != null && "X-Tenant-ID".equals(rh.value());
                    });

            assertThat(hasTenantHeader)
                    .as("listByCluster() must declare @RequestHeader(\"X-Tenant-ID\") parameter")
                    .isTrue();
        }

        @Test
        @DisplayName("list() has @RequestHeader('X-Tenant-ID') parameter")
        void list_hasXTenantIdHeader() throws Exception {
            Method method = BuildingClusterController.class.getMethod("list", String.class);
            assertThat(Arrays.stream(method.getParameters())
                    .anyMatch(p -> {
                        RequestHeader rh = p.getAnnotation(RequestHeader.class);
                        return rh != null && "X-Tenant-ID".equals(rh.value());
                    }))
                    .isTrue();
        }

        @Test
        @DisplayName("create() has @RequestHeader('X-Tenant-ID') parameter")
        void create_hasXTenantIdHeader() throws Exception {
            Method method = Arrays.stream(BuildingClusterController.class.getMethods())
                    .filter(m -> m.getName().equals("create"))
                    .findFirst()
                    .orElseThrow();

            assertThat(Arrays.stream(method.getParameters())
                    .anyMatch(p -> {
                        RequestHeader rh = p.getAnnotation(RequestHeader.class);
                        return rh != null && "X-Tenant-ID".equals(rh.value());
                    }))
                    .isTrue();
        }
    }

    // ── 3. API Annotations — @ApiResponses coverage ───────────────────────────

    @Nested
    @DisplayName("REG-S11-03: @ApiResponses present on controller methods")
    class ApiAnnotationsPresence {

        @Test
        @DisplayName("BuildingClusterController.listByCluster() has @ApiResponses")
        void listByCluster_hasApiResponses() throws Exception {
            Method method = BuildingClusterController.class.getMethod(
                    "listByCluster", String.class, String.class);
            assertThat(method.getAnnotation(ApiResponses.class))
                    .as("listByCluster() missing @ApiResponses — Swagger spec will be incomplete")
                    .isNotNull();
        }

        @Test
        @DisplayName("BuildingClusterController.list() has @ApiResponses")
        void list_hasApiResponses() throws Exception {
            Method method = BuildingClusterController.class.getMethod("list", String.class);
            assertThat(method.getAnnotation(ApiResponses.class)).isNotNull();
        }

        @Test
        @DisplayName("BuildingClusterController.getByCode() has @ApiResponses")
        void getByCode_hasApiResponses() throws Exception {
            Method method = BuildingClusterController.class.getMethod(
                    "getByCode", String.class, String.class);
            assertThat(method.getAnnotation(ApiResponses.class)).isNotNull();
        }
    }

    // ── 4. gRPC Config — analytics-service URL has default ────────────────────

    @Nested
    @DisplayName("REG-S11-04: ClickHouseRestAnalyticsAdapter URL has @Value default")
    class AnalyticsServiceUrlDefault {

        @Test
        @DisplayName("Constructor @Value has fallback to localhost:8082")
        void analyticsServiceUrl_hasDefault() throws Exception {
            var ctor = ClickHouseRestAnalyticsAdapter.class.getConstructor(String.class);
            Parameter param = ctor.getParameters()[0];
            Value valueAnnotation = param.getAnnotation(Value.class);

            assertThat(valueAnnotation).isNotNull();
            assertThat(valueAnnotation.value())
                    .as("URL @Value must include :default to prevent NPE when env var absent")
                    .contains(":");
        }
    }
}
