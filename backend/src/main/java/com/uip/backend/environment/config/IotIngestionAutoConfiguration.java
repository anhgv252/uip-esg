package com.uip.backend.environment.config;

import com.uip.backend.environment.repository.SensorRepository;
import com.uip.backend.environment.service.EnvironmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Demo extraction pattern (ADR-011): IotIngestion module trong monolith.
 *
 * Khi uip.capabilities.iot-ingestion-external = false (default / không set):
 *   matchIfMissing = true → configuration NÀY được load → monolith xử lý IoT.
 *   Dùng cho Tier 1 (1-2 tòa). Tier 1 không cần set flag nào.
 *
 * Khi uip.capabilities.iot-ingestion-external = true:
 *   configuration NÀY bị skip → external iot-ingestion-service đảm nhận.
 *   Monolith chỉ consume từ Kafka topic — topic name không đổi, transparent.
 *   Dùng cho Tier 2 (5-20 tòa) sau khi iot-service đã deployed và shadow-validated.
 *
 * Strangler fig cutover (ADR-011 bước 4):
 *   Set iot-ingestion-external=true trong values-tier2.yaml → bean không load.
 */
@Configuration
@ConditionalOnProperty(
    name           = "uip.capabilities.iot-ingestion-external",
    havingValue    = "false",
    matchIfMissing = true   // ← Tier 1 không set flag → vẫn load bình thường
)
@Slf4j
public class IotIngestionAutoConfiguration {

    @Bean
    public SensorIngestionOrchestrator sensorIngestionOrchestrator(
            SensorRepository sensorRepository,
            EnvironmentService environmentService) {
        log.info("[Capability] iot-ingestion-external=false " +
                 "→ SensorIngestionOrchestrator loaded in monolith");
        return new SensorIngestionOrchestrator(sensorRepository, environmentService);
    }
}
