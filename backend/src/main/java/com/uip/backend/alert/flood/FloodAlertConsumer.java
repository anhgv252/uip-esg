package com.uip.backend.alert.flood;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * S6-FL02 — Flood Alert Kafka Consumer.
 *
 * Listens to {@code UIP.flink.alert.flood.v1} (produced by FloodAlertJob),
 * maps FloodAlertEvent → AlertEvent entity, persists, and publishes to Redis
 * for SSE push (reusing the same pattern as AlertEventKafkaConsumer).
 *
 * Severity mapping: P0_EMERGENCY → CRITICAL, P1_WARNING → HIGH, P2_ADVISORY → WARNING
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FloodAlertConsumer {

    static final String TOPIC = "UIP.flink.alert.flood.v1";
    private static final String DLQ_TOPIC = "UIP.flink.alert.flood.v1.dlq";
    private static final int MAX_RETRIES = 3;
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);
    private static final String SSE_CHANNEL = "uip:alerts";

    private final AlertEventRepository alertEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = TOPIC,
        groupId = "uip-backend-flood-alerts",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String payload, Acknowledgment ack,
                        @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
                        @Header(value = "x-retry-count", required = false, defaultValue = "0") int retryCount) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            AlertEvent event = mapToAlertEvent(data);

            // Dedup: 5-min window per sensor+measureType+severity
            String dedupKey = "alert:dedup:flood:%s:%s:%s".formatted(
                    event.getSensorId(), event.getMeasureType(), event.getSeverity());
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);

            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Duplicate flood alert suppressed: sensor={} severity={}",
                        event.getSensorId(), event.getSeverity());
                ack.acknowledge();
                return;
            }

            AlertEvent saved;
            TenantContext.setCurrentTenant(event.getTenantId());
            try {
                saved = alertEventRepository.save(event);
            } finally {
                TenantContext.clear();
            }
            publishToRedis(saved);

            log.info("Flood alert persisted and published: sensor={} severity={} module=FLOOD",
                    saved.getSensorId(), saved.getSeverity());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process flood alert (attempt {}/{}): {}",
                    retryCount + 1, MAX_RETRIES, e.getMessage(), e);
            if (retryCount + 1 >= MAX_RETRIES) {
                try {
                    kafkaTemplate.send(DLQ_TOPIC, payload);
                    log.warn("Flood alert sent to DLQ after {} failed attempts", MAX_RETRIES);
                } catch (Exception dlqEx) {
                    log.error("Failed to send flood alert to DLQ: {}", dlqEx.getMessage());
                }
                ack.acknowledge();
            }
            // else: do not ack — Kafka will redeliver
        }
    }

    private AlertEvent mapToAlertEvent(Map<String, Object> data) {
        AlertEvent event = new AlertEvent();
        event.setSensorId(getString(data, "sensorId"));
        event.setModule("FLOOD");
        event.setMeasureType(getString(data, "sensorType"));
        event.setValue(getDouble(data, "value"));
        event.setThreshold(getDouble(data, "threshold"));
        event.setSeverity(mapSeverity(getString(data, "severity")));
        event.setTenantId(getString(data, "tenantId"));
        event.setLocation(getString(data, "district"));
        event.setDetectedAt(parseTimestamp(data));
        event.setStatus("OPEN");
        return event;
    }

    /** Map Flink CEP severity to AlertEvent severity. */
    static String mapSeverity(String flinkSeverity) {
        if (flinkSeverity == null) return "WARNING";
        return switch (flinkSeverity) {
            case "P0_EMERGENCY" -> "CRITICAL";
            case "P1_WARNING" -> "HIGH";
            case "P2_ADVISORY" -> "WARNING";
            default -> "WARNING";
        };
    }

    private void publishToRedis(AlertEvent event) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "id", event.getId().toString(),
                    "sensorId", event.getSensorId(),
                    "module", event.getModule(),
                    "severity", event.getSeverity(),
                    "measureType", event.getMeasureType(),
                    "value", event.getValue(),
                    "status", event.getStatus(),
                    "location", event.getLocation() != null ? event.getLocation() : ""
            ));
            redisTemplate.convertAndSend(SSE_CHANNEL, json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to publish flood alert to Redis: {}", e.getMessage());
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

    /** Parse timestamp from Flink event, fallback to now() if missing. */
    private static Instant parseTimestamp(Map<String, Object> data) {
        Object ts = data.get("timestamp");
        if (ts instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue());
        }
        return Instant.now();
    }
}
