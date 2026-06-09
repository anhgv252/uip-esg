package com.uip.backend.esg.config.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests that exactly ONE AnalyticsPort bean is active per deployment profile.
 *
 * This test is a REGRESSION DETECTOR for the P1-2 bug identified in SA Code Review:
 *   "When analytics-external=true AND analytics-transport=grpc, both
 *    ClickHouseRestAnalyticsAdapter and ClickHouseGrpcAnalyticsAdapter load
 *    → NoUniqueBeanDefinitionException at startup."
 *
 * The mutualExclusivity_grpcTransport_restAdapterShouldNotLoad test WILL FAIL
 * until ClickHouseRestAnalyticsAdapter adds:
 *   @ConditionalOnProperty(name="uip.capabilities.analytics-transport",
 *                          havingValue="rest", matchIfMissing=true)
 *
 * Uses ApplicationContextRunner for lightweight condition evaluation (no full
 * Spring Boot context — no Testcontainers required).
 */
@DisplayName("AnalyticsPort bean mutual exclusivity")
class AnalyticsPortMutualExclusivityIT {

    /** Shared runner: only loads the three adapter classes + TimescaleDB config */
    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AnalyticsAutoConfiguration.class))
            .withBean("esgMetricRepository",
                      com.uip.backend.esg.repository.EsgMetricRepository.class,
                      () -> mock(com.uip.backend.esg.repository.EsgMetricRepository.class))
            // Register both Tier-2 adapters so the condition logic is exercised
            .withUserConfiguration(ClickHouseRestAnalyticsAdapter.class,
                                   ClickHouseGrpcAnalyticsAdapter.class);

    // ── Tier 1 (default) ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tier 1: analytics-external not set (default)")
    class Tier1 {

        @Test
        @DisplayName("TimescaleDbAnalyticsAdapter loads when analytics-external is absent")
        void defaultProfile_loads_timescaleAdapter() {
            baseRunner.run(ctx -> {
                assertThat(ctx).hasSingleBean(AnalyticsPort.class);
                assertThat(ctx.getBean(AnalyticsPort.class))
                        .isInstanceOf(TimescaleDbAnalyticsAdapter.class);
            });
        }

        @Test
        @DisplayName("No ClickHouseRestAnalyticsAdapter bean present in Tier 1")
        void defaultProfile_noRestAdapter() {
            baseRunner.run(ctx ->
                assertThat(ctx).doesNotHaveBean(ClickHouseRestAnalyticsAdapter.class));
        }
    }

    // ── Tier 2 REST ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tier 2 REST: analytics-external=true, transport absent")
    class Tier2Rest {

        private final ApplicationContextRunner runner = baseRunner.withPropertyValues(
                "uip.capabilities.analytics-external=true",
                "uip.analytics-service.url=http://localhost:8082");

        @Test
        @DisplayName("ClickHouseRestAnalyticsAdapter is the single AnalyticsPort bean")
        void restProfile_loads_restAdapter() {
            runner.run(ctx -> {
                assertThat(ctx).hasSingleBean(AnalyticsPort.class);
                assertThat(ctx.getBean(AnalyticsPort.class))
                        .isInstanceOf(ClickHouseRestAnalyticsAdapter.class);
            });
        }

        @Test
        @DisplayName("TimescaleDbAnalyticsAdapter does NOT load when analytics-external=true")
        void restProfile_noTimescaleAdapter() {
            runner.run(ctx ->
                assertThat(ctx).doesNotHaveBean(TimescaleDbAnalyticsAdapter.class));
        }
    }

    // ── Tier 2 gRPC ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tier 2 gRPC: analytics-external=true, transport=grpc")
    class Tier2Grpc {

        /**
         * REGRESSION TEST FOR P1-2 BUG.
         *
         * Expected: REST adapter condition EXCLUDES itself when transport=grpc.
         * Pre-fix:  context will fail with NoUniqueBeanDefinitionException.
         * Post-fix: exactly one AnalyticsPort bean (gRPC adapter).
         *
         * The gRPC adapter requires @GrpcClient — in ApplicationContextRunner without
         * full autoconfiguration, the stub field stays null at context load time
         * (condition evaluation only; no actual RPC calls made).
         */
        @Test
        @DisplayName("[P1-2 regression] REST adapter must NOT load when transport=grpc")
        void mutualExclusivity_grpcTransport_restAdapterShouldNotLoad() {
            baseRunner
                    .withPropertyValues(
                            "uip.capabilities.analytics-external=true",
                            "uip.capabilities.analytics-transport=grpc",
                            "uip.analytics-service.url=http://localhost:8082")
                    .run(ctx ->
                        assertThat(ctx).doesNotHaveBean(ClickHouseRestAnalyticsAdapter.class));
        }

        @Test
        @DisplayName("[P1-2 regression] ClickHouseGrpcAnalyticsAdapter loads when transport=grpc")
        void grpcTransport_grpcAdapterPresent() {
            baseRunner
                    .withPropertyValues(
                            "uip.capabilities.analytics-external=true",
                            "uip.capabilities.analytics-transport=grpc")
                    .run(ctx ->
                        assertThat(ctx).hasSingleBean(ClickHouseGrpcAnalyticsAdapter.class));
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge: analytics-external=false explicit")
    class ExplicitFalse {

        @Test
        @DisplayName("TimescaleDb adapter loads when analytics-external=false (explicit)")
        void explicitFalse_loads_timescaleAdapter() {
            baseRunner
                    .withPropertyValues("uip.capabilities.analytics-external=false")
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(AnalyticsPort.class);
                        assertThat(ctx.getBean(AnalyticsPort.class))
                                .isInstanceOf(TimescaleDbAnalyticsAdapter.class);
                    });
        }
    }
}
