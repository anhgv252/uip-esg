package com.uip.backend.alert.service;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.alert.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Evaluates sensor readings against alert rules.
 * Deduplicates via Redis key with TTL = rule.cooldownMinutes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEngine {

    private final AlertRuleRepository  alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final StringRedisTemplate  redisTemplate;

    @Transactional
    public void evaluate(String module, String sensorId, String measureType, double value) {
        List<AlertRule> rules = alertRuleRepository.findByModuleAndActiveTrue(module);

        for (AlertRule rule : rules) {
            if (!rule.getMeasureType().equalsIgnoreCase(measureType)) continue;
            if (!matches(rule.getOperator(), value, rule.getThreshold())) continue;

            String dedupKey = "alert:dedup:%s:%s:%s".formatted(sensorId, measureType, rule.getId());
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, "1", Duration.ofMinutes(rule.getCooldownMinutes()));

            if (Boolean.TRUE.equals(isNew)) {
                AlertEvent event = new AlertEvent();
                event.setRuleId(rule.getId());
                event.setSensorId(sensorId);
                event.setModule(module);
                event.setMeasureType(measureType);
                event.setValue(value);
                event.setThreshold(rule.getThreshold());
                event.setSeverity(rule.getSeverity());
                event.setDetectedAt(Instant.now());
                alertEventRepository.save(event);
                log.info("Alert created: sensor={} measure={} value={} severity={}",
                        sensorId, measureType, value, rule.getSeverity());
            }
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
