package com.uip.backend.alert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.kafka.AlertEventKafkaConsumer;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.alert.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Evaluates sensor readings against alert rules (inline path — không qua Flink).
 * Dùng cho sensor ingestion trực tiếp vào Spring Boot (không qua Flink job).
 *
 * Deduplicates via Redis key with TTL = rule.cooldownMinutes.
 * Sau khi save, publish lên Redis channel để NotificationService đẩy SSE.
 *
 * Lưu ý: Flink path dùng AlertEventKafkaConsumer (topic: UIP.flink.alert.detected.v1).
 * Cả hai path đều publish cùng Redis channel "uip:alerts" → NotificationService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEngine {

    private final AlertRuleRepository  alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final StringRedisTemplate  redisTemplate;
    private final ObjectMapper         objectMapper;

    @Transactional
    public void evaluate(String module, String sensorId, String measureType, double value) {
        List<AlertRule> rules = alertRuleRepository.findByModuleAndActiveTrue(module);

        for (AlertRule rule : rules) {
            if (!rule.getMeasureType().equalsIgnoreCase(measureType)) continue;
            if (!matches(rule.getOperator(), value, rule.getThreshold())) continue;

            String dedupKey = "alert:dedup:%s:%s:%s".formatted(sensorId, measureType, rule.getId());
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", Duration.ofMinutes(rule.getCooldownMinutes()));

            // Fail-open: null = Redis unavailable → tạo alert để không miss P0/P1.
            if (!Boolean.FALSE.equals(isNew)) {
                AlertEvent event = new AlertEvent();
                event.setRuleId(rule.getId());
                event.setSensorId(sensorId);
                event.setModule(module);
                event.setMeasureType(measureType);
                event.setValue(value);
                event.setThreshold(rule.getThreshold());
                event.setSeverity(rule.getSeverity());
                event.setDetectedAt(Instant.now());
                AlertEvent saved = alertEventRepository.save(event);
                publishToRedis(saved);
                log.info("Alert created: sensor={} measure={} value={} severity={}",
                        sensorId, measureType, value, rule.getSeverity());
            }
        }
    }

    private void publishToRedis(AlertEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(AlertEventKafkaConsumer.ALERT_REDIS_CHANNEL, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize alert for Redis publish alertId={}", event.getId(), e);
        }
    }

    private boolean matches(String operator, double value, double threshold) {
        return switch (operator) {
            case ">"  -> value > threshold;
            case ">=" -> value >= threshold;
            case "<"  -> value < threshold;
            case "<=" -> value <= threshold;
            case "==" -> Double.compare(value, threshold) == 0;
            default   -> false;
        };
    }
}
