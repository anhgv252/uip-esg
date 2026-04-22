package com.uip.backend.workflow.trigger;

import com.uip.backend.alert.kafka.AlertEventKafkaConsumer;
import com.uip.backend.common.exception.WorkflowNotFoundException;
import com.uip.backend.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * AI-C03: DISABLED by S4-10 Config Engine.
 * Replaced by GenericKafkaTriggerService + trigger_config row: aiC03_floodEmergencyEvacuation.
 * Uncomment @Component to rollback.
 */
// @Component
@RequiredArgsConstructor
@Slf4j
public class FloodWorkflowTriggerService {

    public static final String PROCESS_KEY        = "aiC03_floodEmergencyEvacuation";
    public static final double FLOOD_THRESHOLD_M  = 3.5;

    private final WorkflowService workflowService;

    @KafkaListener(
            topics           = AlertEventKafkaConsumer.TOPIC,
            groupId          = "uip-workflow-flood",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAlertEvent(Map<String, Object> payload, Acknowledgment ack) {
        try {
            String module      = getString(payload, "module");
            String measureType = getString(payload, "measureType");
            Double value       = getDouble(payload, "value");

            boolean isFloodEvent = "FLOOD".equalsIgnoreCase(module)
                    || "WATER_LEVEL".equalsIgnoreCase(measureType);

            if (!isFloodEvent) {
                ack.acknowledge();
                return;
            }

            if (value == null || value <= FLOOD_THRESHOLD_M) {
                ack.acknowledge();
                return;
            }

            Map<String, Object> variables = Map.of(
                    "scenarioKey",    PROCESS_KEY,
                    "waterLevel",     value,
                    "sensorLocation", getOrDefault(payload, "sensorId", "UNKNOWN"),
                    "warningZones",   getOrDefault(payload, "districtCode", "UNKNOWN"),
                    "detectedAt",     getOrDefault(payload, "detectedAt", Instant.now().toString())
            );

            workflowService.startProcess(PROCESS_KEY, variables);
            log.info("AI-C03 process started: location={}, waterLevel={}",
                    variables.get("sensorLocation"), value);
            ack.acknowledge();

        } catch (WorkflowNotFoundException e) {
            log.warn("AI-C03 process not deployed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to start AI-C03 workflow: {}", e.getMessage(), e);
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
