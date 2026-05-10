package com.uip.backend.environment.config;

import com.uip.backend.environment.repository.SensorRepository;
import com.uip.backend.environment.service.EnvironmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Demo bean: đại diện cho toàn bộ IoT ingestion logic trong monolith.
 *
 * Trong thực tế sẽ chứa: MQTT bridge listener, sensor reading ingestor,
 * device registry sync. Ở đây chỉ là demo skeleton.
 *
 * Bean này CHỈ được tạo khi IotIngestionAutoConfiguration được load
 * (tức là iot-ingestion-external=false hoặc không set).
 */
@RequiredArgsConstructor
@Slf4j
public class SensorIngestionOrchestrator {

    private final SensorRepository sensorRepository;
    @SuppressWarnings("unused") // injected for future ingestion pipeline wiring
    private final EnvironmentService environmentService;

    public String status() {
        long sensorCount = sensorRepository.count();
        return String.format("monolith-mode: %d sensors registered", sensorCount);
    }
}
