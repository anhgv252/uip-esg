package com.uip.backend.alert.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Consumes alert events from Flink (topic: UIP.flink.alert.detected.v1).
 *
 * Trách nhiệm của alert module:
 *   1. Persist AlertEvent vào DB (alerts.alert_events)
 *   2. Publish lên Redis channel để notification module đẩy SSE
 *
 * notification module KHÔNG cần biết AlertEvent domain — chỉ nhận JSON từ Redis.
 *
 * Topic registry: docs/deployment/kafka-topic-registry.xlsx
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertEventKafkaConsumer {

    /** Topic produced by Flink AlertDetectionJob — see kafka-topic-registry.md */
    public static final String TOPIC = "UIP.flink.alert.detected.v1";

    public static final String ALERT_REDIS_CHANNEL = "uip:alerts";

    private final AlertEventRepository alertEventRepository;
    private final StringRedisTemplate  redisTemplate;
    private final ObjectMapper         objectMapper;

    /**
     * Dedup TTL: 5 phút — cửa sổ đủ rộng để hấp thụ Kafka retry / at-least-once redelivery.
     * Key: alert:dedup:kafka:{sensorId}:{measureType}:{severity}
     * Cùng logic với AlertEngine nhưng không dùng ruleId (Flink không biết rule nội bộ).
     */
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    @KafkaListener(
        topics           = AlertEventKafkaConsumer.TOPIC,
        groupId          = "uip-backend-alerts",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(Map<String, Object> payload, Acknowledgment ack) {
        try {
            AlertEvent event = mapToAlertEvent(payload);

            String dedupKey = "alert:dedup:kafka:%s:%s:%s".formatted(
                    event.getSensorId(), event.getMeasureType(), event.getSeverity());
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);

            // Fail-open: null = Redis unavailable → xử lý alert để không miss P0/P1.
            // Chỉ suppress khi Redis trả FALSE (confirmed duplicate trong dedup window).
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Duplicate alert suppressed (dedup): sensor={} measure={} severity={}",
                        event.getSensorId(), event.getMeasureType(), event.getSeverity());
                ack.acknowledge();
                return;
            }

            AlertEvent saved = alertEventRepository.save(event);
            publishToRedis(saved);

            log.info("Alert persisted and published: sensor={} severity={}",
                    saved.getSensorId(), saved.getSeverity());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process alert event from Kafka: {}", e.getMessage(), e);
            // Do not ack — will be retried
        }
    }

    private void publishToRedis(AlertEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(ALERT_REDIS_CHANNEL, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize alert for Redis publish alertId={}", event.getId(), e);
        }
    }

    private AlertEvent mapToAlertEvent(Map<String, Object> m) {
        AlertEvent e = new AlertEvent();
        e.setSensorId(getString(m, "sensorId"));
        e.setModule(getString(m, "module"));
        e.setMeasureType(getString(m, "measureType"));
        e.setValue(getDouble(m, "value"));
        e.setThreshold(getDouble(m, "threshold"));
        e.setSeverity(getString(m, "severity"));
        String detectedAtStr = getString(m, "detectedAt");
        e.setDetectedAt(detectedAtStr != null ? Instant.parse(detectedAtStr) : Instant.now());
        return e;
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
}
