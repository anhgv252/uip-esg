package com.uip.backend.notification.kafka;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Consumes alert events from Flink (topic: alert_events) and:
 * 1. Persists to alerts.alert_events table
 * 2. Publishes to Redis → SSE push to clients
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertEventKafkaConsumer {

    private final AlertEventRepository alertEventRepository;
    private final NotificationService  notificationService;

    @KafkaListener(
        topics        = "alert_events",
        groupId       = "uip-backend-alerts",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(Map<String, Object> payload, Acknowledgment ack) {
        try {
            AlertEvent event = mapToAlertEvent(payload);
            AlertEvent saved = alertEventRepository.save(event);
            notificationService.publishAlert(saved);
            log.info("Alert persisted and pushed: sensor={} severity={}",
                    event.getSensorId(), event.getSeverity());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process alert event from Kafka: {}", e.getMessage(), e);
            // Do not ack — will be retried
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
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
