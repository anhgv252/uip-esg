package com.uip.backend.safety.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.common.spi.AlertPort;
import com.uip.backend.common.spi.AlertPort.SavedAlertSnapshot;
import com.uip.backend.common.spi.AlertPort.StructuralAlertInput;
import com.uip.backend.common.spi.NotificationPort;
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
 *
 * <p>ADR-052 (migration C1+C2): interacts with {@code alert} and {@code notification} modules
 * only through neutral {@link AlertPort} / {@link NotificationPort} ports — never imports
 * {@code alert.domain} / {@code alert.service} / {@code notification.service}.</p>
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

    private final AlertPort                 alertPort;
    private final BuildingSafetyService     buildingSafetyService;
    private final NotificationPort          notificationPort;
    private final StringRedisTemplate       redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper              objectMapper;

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
            StructuralAlertInput input = mapToInput(data);

            Set<String> allowedTenants = Set.of(allowedTenantsConfig.split(","));
            if (input.tenantId() == null || !allowedTenants.contains(input.tenantId())) {
                log.warn("Structural alert rejected: unknown tenantId={}", input.tenantId());
                kafkaTemplate.send(DLQ_TOPIC, payload);
                ack.acknowledge();
                return;
            }

            // Dedup: 1-min window per tenant+sensor+measureType+severity
            // (shorter than flood — structural alerts are acute; MVP5-S1-T06: tenant prefix)
            String dedupKey = "alert:dedup:structural:tenant:%s:%s:%s:%s".formatted(
                    input.tenantId(), input.sensorId(), input.measureType(), input.severity());
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);

            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Duplicate structural alert suppressed: sensor={} severity={}",
                        input.sensorId(), input.severity());
                ack.acknowledge();
                return;
            }

            SavedAlertSnapshot saved;
            TenantContext.setCurrentTenant(input.tenantId());
            try {
                saved = alertPort.saveStructuralAlert(input);
            } finally {
                TenantContext.clear();
            }

            // Evict safety score cache so next GET reflects the new alert
            if (saved.buildingId() != null) {
                TenantContext.setCurrentTenant(input.tenantId());
                try {
                    buildingSafetyService.evictSafetyScore(saved.buildingId());
                } finally {
                    TenantContext.clear();
                }
            }

            // P0 escalation: route to all channels (FCM/APNs/Email) — BR-010 message
            // BR-010: notification is REVIEW PROMPT only, NOT an instruction to auto-evacuate
            String notifMessage = buildNotificationMessage(input);
            notificationPort.routeAlert(
                    saved.sensorId(),
                    "STRUCTURAL",
                    saved.severity(),
                    notifMessage,
                    saved.tenantId()
            );

            publishToRedis(saved, input);

            log.info("Structural alert persisted id={} sensor={} severity={} building={}",
                    saved.id(), saved.sensorId(), saved.severity(), saved.buildingId());
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

    /** Map Flink payload to a neutral structural-alert input. */
    private StructuralAlertInput mapToInput(Map<String, Object> data) {
        return new StructuralAlertInput(
                getString(data, "tenantId"),
                getString(data, "sensorId"),
                "STRUCTURAL",
                getString(data, "sensorType"),
                getDouble(data, "measuredValue"),
                getDouble(data, "thresholdValue"),
                mapSeverity(getString(data, "severity")),
                getString(data, "buildingId"),
                getString(data, "district"),
                parseTimestamp(data),
                "OPEN"
        );
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
    private static String buildNotificationMessage(StructuralAlertInput input) {
        String type = input.measureType() != null ? input.measureType() : "Structural";
        String loc  = input.location() != null ? " (" + input.location() + ")" : "";
        return "[BR-010] Phát hiện bất thường kết cấu%s — %s: %.2f. Yêu cầu operator xem xét. KHÔNG tự động sơ tán."
                .formatted(loc, type, input.value());
    }

    private void publishToRedis(SavedAlertSnapshot saved, StructuralAlertInput input) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "id",          saved.id() != null ? saved.id().toString() : "",
                    "sensorId",    saved.sensorId() != null ? saved.sensorId() : "",
                    "module",      "STRUCTURAL",
                    "severity",    saved.severity(),
                    "measureType", input.measureType() != null ? input.measureType() : "",
                    "value",       input.value(),
                    "status",      input.status(),
                    "buildingId",  saved.buildingId() != null ? saved.buildingId() : "",
                    "location",    input.location() != null ? input.location() : "",
                    "tenantId",    saved.tenantId() != null ? saved.tenantId() : ""
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
