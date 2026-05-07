package com.uip.backend.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Delivers notifications via Server-Sent Events to browser clients
 * currently connected to /api/v1/notifications/stream.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseNotificationChannel implements NotificationChannel {

    private final SseEmitterRegistry sseEmitterRegistry;

    @Override
    public void send(AlertNotification notification) {
        Map<String, Object> alertData = Map.of(
                "sensorId", notification.sensorId() != null ? notification.sensorId() : "",
                "module", notification.module() != null ? notification.module() : "",
                "severity", notification.severity() != null ? notification.severity() : "",
                "message", notification.message() != null ? notification.message() : "",
                "tenantId", notification.tenantId() != null ? notification.tenantId() : "",
                "alertId", notification.alertId() != null ? notification.alertId() : 0L
        );
        sseEmitterRegistry.broadcast("alert", alertData);
        log.debug("SSE notification sent for sensor={} tenant={}",
                notification.sensorId(), notification.tenantId());
    }

    @Override
    public String getChannelName() {
        return "sse";
    }
}
