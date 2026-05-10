package com.uip.backend.environment.config;

import com.uip.backend.esg.config.analytics.AnalyticsAutoConfiguration;
import com.uip.backend.esg.config.analytics.AnalyticsPort;
import com.uip.backend.esg.config.analytics.ClickHouseRestAnalyticsAdapter;
import com.uip.backend.esg.config.analytics.TimescaleDbAnalyticsAdapter;
import com.uip.backend.esg.repository.EsgMetricRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demo extraction pattern (ADR-011): chứng minh capability flags hoạt động đúng.
 *
 * Dùng ApplicationContextRunner — không khởi động full Spring app, không cần DB/Kafka.
 * Chạy nhanh (<1s), phù hợp trong CI cho mọi PR có extraction code.
 *
 * Hai kịch bản được test:
 *   Tier 1 (không set flag)   → IotIngestionAutoConfiguration LOAD, SensorIngestionOrchestrator tồn tại
 *   Tier 2 (flag = true)       → IotIngestionAutoConfiguration SKIP,  SensorIngestionOrchestrator KHÔNG tồn tại
 */
@DisplayName("Capability Flag — Extraction Demo")
class CapabilityFlagIT {

    // ── IotIngestion extraction ───────────────────────────────────────────────

    @Nested
    @DisplayName("IotIngestionAutoConfiguration")
    class IotIngestionFlagTests {

        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IotIngestionAutoConfiguration.class);

        @Test
        @DisplayName("Tier 1 — flag không set → SensorIngestionOrchestrator được tạo (monolith mode)")
        void tier1_noFlag_orchestratorPresent() {
            // Không set bất kỳ property nào → matchIfMissing=true → bean load
            contextRunner
                .withBean("sensorRepository",
                    com.uip.backend.environment.repository.SensorRepository.class,
                    () -> org.mockito.Mockito.mock(
                        com.uip.backend.environment.repository.SensorRepository.class))
                .withBean("environmentService",
                    com.uip.backend.environment.service.EnvironmentService.class,
                    () -> org.mockito.Mockito.mock(
                        com.uip.backend.environment.service.EnvironmentService.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SensorIngestionOrchestrator.class);
                    // Tier 1 monolith: IoT logic chạy trong cùng JVM
                });
        }

        @Test
        @DisplayName("Tier 1 — flag=false tường minh → SensorIngestionOrchestrator được tạo")
        void tier1_flagFalse_orchestratorPresent() {
            contextRunner
                .withPropertyValues("uip.capabilities.iot-ingestion-external=false")
                .withBean("sensorRepository",
                    com.uip.backend.environment.repository.SensorRepository.class,
                    () -> org.mockito.Mockito.mock(
                        com.uip.backend.environment.repository.SensorRepository.class))
                .withBean("environmentService",
                    com.uip.backend.environment.service.EnvironmentService.class,
                    () -> org.mockito.Mockito.mock(
                        com.uip.backend.environment.service.EnvironmentService.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SensorIngestionOrchestrator.class);
                });
        }

        @Test
        @DisplayName("Tier 2 — flag=true → SensorIngestionOrchestrator KHÔNG được tạo (external service mode)")
        void tier2_flagTrue_orchestratorAbsent() {
            // Sau cutover Sprint 5: iot-ingestion-external=true
            // SensorIngestionOrchestrator bean không được tạo trong monolith JVM
            contextRunner
                .withPropertyValues("uip.capabilities.iot-ingestion-external=true")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(SensorIngestionOrchestrator.class);
                    // iot-ingestion-service ngoài xử lý BMS + MQTT, produce vào Kafka
                    // Monolith consume từ cùng topic — transparent với alert/esg modules
                });
        }
    }

    // ── Analytics extraction ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AnalyticsAutoConfiguration — Port Interface swap")
    class AnalyticsFlagTests {

        // TimescaleDbAnalyticsAdapter cần EsgMetricRepository — mock để không load JPA stack
        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                AnalyticsAutoConfiguration.class,
                ClickHouseRestAnalyticsAdapter.class)
            .withBean("esgMetricRepository",
                EsgMetricRepository.class,
                () -> Mockito.mock(EsgMetricRepository.class));

        @Test
        @DisplayName("Tier 1 — không set flag → TimescaleDbAnalyticsAdapter load")
        void tier1_noFlag_timescaleAdapterLoaded() {
            contextRunner.run(ctx -> {
                assertThat(ctx).hasSingleBean(AnalyticsPort.class);
                assertThat(ctx.getBean(AnalyticsPort.class))
                    .isInstanceOf(TimescaleDbAnalyticsAdapter.class);
            });
        }

        @Test
        @DisplayName("Tier 2 — analytics-external=true → ClickHouseRestAnalyticsAdapter load")
        void tier2_analyticsExternal_clickhouseAdapterLoaded() {
            contextRunner
                .withPropertyValues(
                    "uip.capabilities.analytics-external=true",
                    // URL required when ClickHouseRestAnalyticsAdapter is loaded
                    "uip.analytics-service.url=http://localhost:8081/api/v1/analytics")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AnalyticsPort.class);
                    assertThat(ctx.getBean(AnalyticsPort.class))
                        .isInstanceOf(ClickHouseRestAnalyticsAdapter.class);
                });
        }

        @Test
        @DisplayName("Port Interface contract: cả hai adapter đều implement AnalyticsPort")
        void bothAdaptersImplementSameInterface() {
            // EsgService chỉ biết AnalyticsPort, không biết TimescaleDb hay ClickHouse
            assertThat(TimescaleDbAnalyticsAdapter.class)
                .isAssignableTo(AnalyticsPort.class);
            assertThat(ClickHouseRestAnalyticsAdapter.class)
                .isAssignableTo(AnalyticsPort.class);
        }
    }
}
