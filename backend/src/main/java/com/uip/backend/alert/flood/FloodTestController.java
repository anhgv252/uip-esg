package com.uip.backend.alert.flood;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * S6-FL04 — Test-only controller for flood alert demo scenarios.
 * Double-gated: requires @Profile("test") AND features.test.flood-controller.enabled=true.
 * tenantId is always taken from JWT (TenantContext) — never from caller input.
 */
@RestController
@RequestMapping("/api/v1/test")
@Profile("!production")
@ConditionalOnProperty(name = "features.test.flood-controller.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Hidden
public class FloodTestController {

    private static final Set<String> VALID_SEVERITIES = Set.of(
            "P0_EMERGENCY", "P1_WARNING", "P2_ADVISORY");

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @PostMapping("/inject-reading")
    public ResponseEntity<Map<String, Object>> injectReading(
            @RequestParam(defaultValue = "SENSOR-FLOOD-001") String sensorId,
            @RequestParam(defaultValue = "RAINFALL") String sensorType,
            @RequestParam(defaultValue = "90.0") double value,
            @RequestParam(defaultValue = "district-1") String district) {

        String tenantId = TenantContext.getCurrentTenant();

        try {
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

            log.info("Injected test reading: sensor={} type={} value={} district={} tenant={}",
                    sensorId, sensorType, value, district, tenantId);

            return ResponseEntity.ok(Map.of(
                    "status", "injected",
                    "sensorId", sensorId,
                    "sensorType", sensorType,
                    "value", value,
                    "tenantId", tenantId,
                    "message", "Reading injected to ngsi_ld_environment."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/inject-flood-alert")
    public ResponseEntity<Map<String, Object>> injectFloodAlert(
            @RequestParam(defaultValue = "SENSOR-FLOOD-001") String sensorId,
            @RequestParam(defaultValue = "RAINFALL") String sensorType,
            @RequestParam(defaultValue = "90.0") double value,
            @RequestParam(defaultValue = "district-1") String district,
            @RequestParam(defaultValue = "P1_WARNING") String severity) {

        if (!VALID_SEVERITIES.contains(severity)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid severity. Must be one of: " + VALID_SEVERITIES));
        }

        String tenantId = TenantContext.getCurrentTenant();

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

            log.info("Injected direct flood alert: sensor={} severity={} tenant={}",
                    sensorId, severity, tenantId);

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
