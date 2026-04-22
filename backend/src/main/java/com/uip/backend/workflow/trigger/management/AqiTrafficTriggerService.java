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

// DISABLED by S4-10 Config Engine. Replaced by GenericKafkaTriggerService + trigger_config row: aiM02_aqiTrafficControl.
// @Component
@RequiredArgsConstructor
@Slf4j
public class AqiTrafficTriggerService {

    public static final String PROCESS_KEY    = "aiM02_aqiTrafficControl";
    public static final double AQI_THRESHOLD  = 150.0;

    private final WorkflowService workflowService;

    @KafkaListener(
            topics           = AlertEventKafkaConsumer.TOPIC,
            groupId          = "uip-workflow-m02-aqi",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAlertEvent(Map<String, Object> payload, Acknowledgment ack) {
        try {
            String module      = getString(payload, "module");
            String measureType = getString(payload, "measureType");
            Double value       = getDouble(payload, "value");

            if (!"ENVIRONMENT".equalsIgnoreCase(module)
                    || !"AQI".equalsIgnoreCase(measureType)
                    || value == null || value <= AQI_THRESHOLD) {
                ack.acknowledge();
                return;
            }

            Map<String, Object> variables = Map.of(
                    "scenarioKey",       PROCESS_KEY,
                    "sensorId",          getOrDefault(payload, "sensorId", "UNKNOWN"),
                    "aqiValue",          value,
                    "pollutants",        getOrDefault(payload, "measureType", "AQI"),
                    "affectedDistricts", getOrDefault(payload, "districtCode", "UNKNOWN")
            );

            workflowService.startProcess(PROCESS_KEY, variables);
            log.info("AI-M02 started: aqi={}, district={}", value, variables.get("affectedDistricts"));
            ack.acknowledge();

        } catch (WorkflowNotFoundException e) {
            log.warn("AI-M02 process not deployed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to start AI-M02: {}", e.getMessage(), e);
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
