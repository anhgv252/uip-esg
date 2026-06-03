package com.uip.backend.safety.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.service.AlertService;
import com.uip.backend.notification.service.AlertNotification;
import com.uip.backend.notification.service.NotificationRouter;
import com.uip.backend.safety.service.BuildingSafetyService;
import com.uip.backend.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * S7-B05 — Structural Alert Kafka Consumer.
 *
 * <p>Listens to {@code UIP.structural.alert.critical.v1} (produced by VibrationAnomalyJob),
 * persists the alert, evicts the safety score cache, and dispatches P0 escalation notifications
 * via FCM/APNs/Email within the <15s SLA target.</p>
 *
 * <p><strong>BR-010:</strong> ALL structural P0 alerts require operator review.
 * This consumer NEVER triggers auto-evacuation. Notifications are review prompts only.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StructuralAlertConsumer {

    static final String TOPIC = "UIP.structural.alert.critical.v1";
    private static final String DLQ_TOPIC = "UIP.structural.alert.dlq.v1";
    private static final int MAX_RETRIES = 3;
    private static final Duration DEDUP_TTL = Duration.ofMinutes(1); // short TTL — structural alerts are time-critical
    private static final String SSE_CHANNEL = "uip:alerts";

    @Value("${uip.tenant.allowed-ids:hcm,hanoi,danang}")
    private String allowedTenantsConfig;

    private final AlertService             alertService;
    private final BuildingSafetyService    buildingSafetyService;
    private final NotificationRouter       notificationRouter;
    private final StringRedisTemplate      redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper             objectMapper;

    @KafkaListener(
        topics = TOPIC,
        groupId = "uip-backend-structural-alerts",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String payload, Acknowledgment ack,
                        @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                        @Header(value = "x-retry-count", required = false, defaultValue = "0") int retryCount) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            AlertEvent event = mapToAlertEvent(data);

            Set<String> allowedTenants = Set.of(allowedTenantsConfig.split(","));
            if (event.getTenantId() == null || !allowedTenants.contains(event.getTenantId())) {
                log.warn("Structural alert rejected: unknown tenantId={}", event.getTenantId());
                kafkaTemplate.send(DLQ_TOPIC, payload);
                ack.acknowledge();
                return;
            }

            // Dedup: 1-min window per sensor+measureType+severity (shorter than flood — structural alerts are acute)
            String dedupKey = "alert:dedup:structural:%s:%s:%s".formatted(
                    event.getSensorId(), event.getMeasureType(), event.getSeverity());
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);

            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Duplicate structural alert suppressed: sensor={} severity={}",
                        event.getSensorId(), event.getSeverity());
                ack.acknowledge();
                return;
            }

            AlertEvent saved;
            TenantContext.setCurrentTenant(event.getTenantId());
            try {
                saved = alertService.saveAlert(event);
            } finally {
                TenantContext.clear();
            }

            // Evict safety score cache so next GET reflects the new alert
            if (saved.getBuildingId() != null) {
                TenantContext.setCurrentTenant(event.getTenantId());
                try {
                    buildingSafetyService.evictSafetyScore(saved.getBuildingId());
                } finally {
                    TenantContext.clear();
                }
            }

            // P0 escalation: route to all channels (FCM/APNs/Email) — BR-010 message
            // BR-010: notification is REVIEW PROMPT only, NOT an instruction to auto-evacuate
            String notifMessage = buildNotificationMessage(event);
            notificationRouter.route(new AlertNotification(
                    saved.getSensorId(),
                    "STRUCTURAL",
                    saved.getSeverity(),
                    notifMessage,
                    saved.getTenantId(),
                    null
            ));

            publishToRedis(saved);

            log.info("Structural alert persisted id={} sensor={} severity={} building={}",
                    saved.getId(), saved.getSensorId(), saved.getSeverity(), saved.getBuildingId());
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process structural alert (attempt {}/{}): {}",
                    retryCount + 1, MAX_RETRIES, e.getMessage(), e);
            if (retryCount + 1 >= MAX_RETRIES) {
                try {
                    kafkaTemplate.send(DLQ_TOPIC, payload);
                    log.warn("Structural alert sent to DLQ after {} failed attempts", MAX_RETRIES);
                } catch (Exception dlqEx) {
                    log.error("Failed to send structural alert to DLQ: {}", dlqEx.getMessage());
                }
                ack.acknowledge();
            }
            // else: do not ack — Kafka redelivers
        }
    }

    private AlertEvent mapToAlertEvent(Map<String, Object> data) {
        AlertEvent event = new AlertEvent();
        event.setSensorId(getString(data, "sensorId"));
        event.setModule("STRUCTURAL");
        event.setMeasureType(getString(data, "sensorType"));
        event.setValue(getDouble(data, "measuredValue"));
        event.setThreshold(getDouble(data, "thresholdValue"));
        event.setSeverity(mapSeverity(getString(data, "severity")));
        event.setTenantId(getString(data, "tenantId"));
        event.setBuildingId(getString(data, "buildingId"));
        event.setLocation(getString(data, "district"));
        event.setDetectedAt(parseTimestamp(data));
        event.setStatus("OPEN");
        return event;
    }

    /** Map Flink structural severity to AlertEvent severity. CRITICAL stays CRITICAL. */
    static String mapSeverity(String flinkSeverity) {
        if (flinkSeverity == null) return "WARNING";
        return switch (flinkSeverity) {
            case "CRITICAL" -> "CRITICAL";
            case "WARNING"  -> "HIGH";
            default         -> "WARNING";
        };
    }

    // BR-010: message explicitly states operator review is required, NOT auto-evacuate
    private static String buildNotificationMessage(AlertEvent event) {
        String type = event.getMeasureType() != null ? event.getMeasureType() : "Structural";
        String loc  = event.getLocation() != null ? " (" + event.getLocation() + ")" : "";
        return "[BR-010] Phát hiện bất thường kết cấu%s — %s: %.2f. Yêu cầu operator xem xét. KHÔNG tự động sơ tán."
                .formatted(loc, type, event.getValue());
    }

    private void publishToRedis(AlertEvent event) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "id",          event.getId().toString(),
                    "sensorId",    event.getSensorId() != null ? event.getSensorId() : "",
                    "module",      "STRUCTURAL",
                    "severity",    event.getSeverity(),
                    "measureType", event.getMeasureType() != null ? event.getMeasureType() : "",
                    "value",       event.getValue(),
                    "status",      event.getStatus(),
                    "buildingId",  event.getBuildingId() != null ? event.getBuildingId() : "",
                    "location",    event.getLocation() != null ? event.getLocation() : "",
                    "tenantId",    event.getTenantId() != null ? event.getTenantId() : ""
            ));
            redisTemplate.convertAndSend(SSE_CHANNEL, json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to publish structural alert to Redis SSE: {}", e.getMessage());
        }
    }

    private static String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    private static Double getDouble(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private static Instant parseTimestamp(Map<String, Object> data) {
        Object ts = data.get("observedAtMillis");
        if (ts instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        return Instant.now();
    }
}
