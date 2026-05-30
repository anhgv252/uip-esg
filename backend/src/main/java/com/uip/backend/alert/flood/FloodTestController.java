package com.uip.backend.alert.flood;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * S6-FL04 — Test-only controller for flood alert demo scenarios.
 * Only active when profile "test" is set.
 *
 * Injects simulated sensor readings that trigger the FloodAlertJob
 * Flink CEP pipeline, producing flood alert events.
 */
@RestController
@RequestMapping("/api/v1/test")
@Profile("test")
@RequiredArgsConstructor
@Slf4j
public class FloodTestController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Inject a simulated sensor reading to the ngsi_ld_environment Kafka topic.
     * This triggers the FloodAlertJob Flink CEP pipeline.
     *
     * Demo: Call this 3 times with value > 50 (P2 threshold) for RAINFALL
     * to trigger a flood alert within 30 seconds.
     */
    @PostMapping("/inject-reading")
    public ResponseEntity<Map<String, Object>> injectReading(
            @RequestParam(defaultValue = "SENSOR-FLOOD-001") String sensorId,
            @RequestParam(defaultValue = "RAINFALL") String sensorType,
            @RequestParam(defaultValue = "90.0") double value,
            @RequestParam(defaultValue = "hcm") String tenantId,
            @RequestParam(defaultValue = "district-1") String district) {

        try {
            // Build NGSI-LD message matching Flink job expected format
            Map<String, Object> ngsiMessage = Map.of(
                    "deviceId", Map.of("type", "Property", "value", sensorId),
                    "observedAt", Map.of("type", "Property", "value", System.currentTimeMillis()),
                    "measurements", Map.of("type", "Property", "value",
                            Map.of(sensorType.toLowerCase(), value)),
                    "_meta", Map.of(
                            "source", "test-injector",
                            "sensorType", sensorType,
                            "tenantId", tenantId,
                            "district", district
                    )
            );

            String json = objectMapper.writeValueAsString(ngsiMessage);
            kafkaTemplate.send("ngsi_ld_environment", sensorId, json);

            log.info("Injected test reading: sensor={} type={} value={} district={}",
                    sensorId, sensorType, value, district);

            return ResponseEntity.ok(Map.of(
                    "status", "injected",
                    "sensorId", sensorId,
                    "sensorType", sensorType,
                    "value", value,
                    "message", "Reading injected to ngsi_ld_environment. Send 3+ readings above P2 threshold to trigger flood alert."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Directly inject a FloodAlertEvent to the flood alert output topic.
     * Bypasses Flink CEP — for demo scenarios where Flink is not running.
     */
    @PostMapping("/inject-flood-alert")
    public ResponseEntity<Map<String, Object>> injectFloodAlert(
            @RequestParam(defaultValue = "SENSOR-FLOOD-001") String sensorId,
            @RequestParam(defaultValue = "RAINFALL") String sensorType,
            @RequestParam(defaultValue = "90.0") double value,
            @RequestParam(defaultValue = "hcm") String tenantId,
            @RequestParam(defaultValue = "district-1") String district,
            @RequestParam(defaultValue = "P1_WARNING") String severity) {

        try {
            Map<String, Object> event = Map.of(
                    "sensorId", sensorId,
                    "sensorType", sensorType,
                    "tenantId", tenantId,
                    "value", value,
                    "threshold", 80.0,
                    "severity", severity,
                    "district", district,
                    "timestamp", System.currentTimeMillis(),
                    "consecutiveCount", 3
            );

            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("UIP.flink.alert.flood.v1", sensorId, json);

            log.info("Injected direct flood alert: sensor={} severity={}", sensorId, severity);

            return ResponseEntity.ok(Map.of(
                    "status", "injected",
                    "topic", "UIP.flink.alert.flood.v1",
                    "event", event
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
