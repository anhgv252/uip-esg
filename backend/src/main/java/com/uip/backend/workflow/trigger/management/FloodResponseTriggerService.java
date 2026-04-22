package com.uip.backend.workflow.trigger.management;

import com.uip.backend.alert.kafka.AlertEventKafkaConsumer;
import com.uip.backend.common.exception.WorkflowNotFoundException;
import com.uip.backend.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

// DISABLED by S4-10 Config Engine. Replaced by GenericKafkaTriggerService + trigger_config row: aiM01_floodResponseCoordination.
// @Component
@RequiredArgsConstructor
@Slf4j
public class FloodResponseTriggerService {

    public static final String PROCESS_KEY      = "aiM01_floodResponseCoordination";
    public static final double FLOOD_THRESHOLD_M = 3.5;

    private final WorkflowService workflowService;

    @KafkaListener(
            topics           = AlertEventKafkaConsumer.TOPIC,
            groupId          = "uip-workflow-m01-flood",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAlertEvent(Map<String, Object> payload, Acknowledgment ack) {
        try {
            String measureType = getString(payload, "measureType");
            Double value       = getDouble(payload, "value");

            if (!"WATER_LEVEL".equalsIgnoreCase(measureType)
                    || value == null || value <= FLOOD_THRESHOLD_M) {
                ack.acknowledge();
                return;
            }

            Map<String, Object> variables = Map.of(
                    "scenarioKey",   PROCESS_KEY,
                    "alertId",       getOrDefault(payload, "alertId", UUID.randomUUID().toString()),
                    "waterLevel",    value,
                    "location",      getOrDefault(payload, "sensorId", "UNKNOWN"),
                    "affectedZones", getOrDefault(payload, "districtCode", "UNKNOWN")
            );

            workflowService.startProcess(PROCESS_KEY, variables);
            log.info("AI-M01 started: location={}, waterLevel={}", variables.get("location"), value);
            ack.acknowledge();

        } catch (WorkflowNotFoundException e) {
            log.warn("AI-M01 process not deployed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to start AI-M01: {}", e.getMessage(), e);
        }
    }

    private String getString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private Double getDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException ex) { return null; }
    }

    private String getOrDefault(Map<String, Object> m, String key, String defaultValue) {
        String val = getString(m, key);
        return val != null ? val : defaultValue;
    }
}
