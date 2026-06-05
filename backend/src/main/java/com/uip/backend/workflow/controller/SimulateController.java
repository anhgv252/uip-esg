package com.uip.backend.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulate")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Simulate", description = "IoT event simulation for demo/testing")
@SecurityRequirement(name = "Bearer Authentication")
public class SimulateController {

    private static final double AQI_ALERT_THRESHOLD = 150.0;
    private static final String NGSI_LD_TOPIC = "ngsi_ld_environment";

    private final WorkflowService workflowService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Simulates an IoT sensor reading event flowing through the full pipeline:
     * MQTT → Kafka → Flink → Alert → Camunda BPMN
     *
     * If the AQI value exceeds the threshold, starts the aiC01_aqiCitizenAlert BPMN process.
     */
    @PostMapping("/iot-sensor")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Simulate IoT sensor reading",
        description = "Simulates a sensor event. If AQI exceeds threshold, triggers Camunda BPMN aiC01_aqiCitizenAlert."
    )
    public ResponseEntity<Map<String, Object>> simulateSensorReading(
            @RequestBody Map<String, Object> payload) {

        String sensorType = (String) payload.getOrDefault("sensorType", "AQI");
        String sensorId   = (String) payload.getOrDefault("sensorId", "ENV-DEMO-001");
        double value      = ((Number) payload.getOrDefault("value", 0.0)).doubleValue();
        String district   = (String) payload.getOrDefault("district", "D1");
        String tenantId   = (String) payload.getOrDefault("tenantId", "default");
        long measuredAtMs = Instant.now().toEpochMilli();
        String measuredAt = Instant.now().toString();

        boolean alertTriggered = false;
        boolean kafkaPublished = false;
        String processInstanceId = "";

        if ("AQI".equals(sensorType) && value > AQI_ALERT_THRESHOLD) {
            alertTriggered = true;

            Map<String, Object> variables = new HashMap<>();
            variables.put("scenarioKey",  "aiC01_aqiCitizenAlert");
            variables.put("sensorId",     sensorId);
            variables.put("aqiValue",     value);
            variables.put("districtCode", district);
            variables.put("measuredAt",   measuredAt);

            ProcessInstanceDto instance = workflowService.startProcess("aiC01_aqiCitizenAlert", variables);
            processInstanceId = instance.getId();

            log.info("IoT simulation: AQI={} from sensor {} in district {} \u2192 alert process started: {}",
                    value, sanitizeLog(sensorId), sanitizeLog(district), sanitizeLog(processInstanceId));
        } else {
            // For non-AQI types or AQI below threshold, publish to Kafka as NgsiLdMessage
            try {
                Map<String, Object> ngsiMessage = new HashMap<>();
                ngsiMessage.put("id", "urn:ngsi-ld:Device:" + sensorId);
                ngsiMessage.put("type", "Device");
                ngsiMessage.put("deviceId", Map.of("type", "Property", "value", sensorId));
                ngsiMessage.put("sensorType", Map.of("type", "Property", "value", sensorType));
                ngsiMessage.put("observedAt", Map.of("type", "Property", "value", measuredAtMs));
                ngsiMessage.put("measurements", Map.of("type", "Property", "value", Map.of("value", value)));
                ngsiMessage.put("_meta", Map.of("tenantId", tenantId, "sensorType", sensorType));

                String ngsiJson = objectMapper.writeValueAsString(ngsiMessage);
                kafkaTemplate.send(NGSI_LD_TOPIC, sensorId, ngsiJson);
                kafkaPublished = true;

                log.info("IoT simulation: {}={} from sensor {} published to Kafka topic {}",
                        sensorType, value, sanitizeLog(sensorId), NGSI_LD_TOPIC);
            } catch (Exception e) {
                log.error("Failed to publish sensor reading to Kafka: sensor={} type={}: {}",
                        sanitizeLog(sensorId), sensorType, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sensorType",        sensorType);
        result.put("sensorId",          sensorId);
        result.put("value",             value);
        result.put("district",          district);
        result.put("measuredAt",        measuredAt);
        result.put("threshold",         AQI_ALERT_THRESHOLD);
        result.put("alertTriggered",    alertTriggered);
        result.put("kafkaPublished",    kafkaPublished);
        result.put("processInstanceId", processInstanceId);

        return ResponseEntity.ok(result);
    }

    private static String sanitizeLog(String input) {
        if (input == null) return "null";
        return input.replaceAll("[\r\n\t]", "_");
    }
}
