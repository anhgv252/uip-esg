package com.uip.backend.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseNotificationChannelTest {

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private SseNotificationChannel channel;

    @BeforeEach
    void setUp() {
        channel = new SseNotificationChannel(sseEmitterRegistry);
    }

    @Test
    void channelName_isSse() {
        assertThat(channel.getChannelName()).isEqualTo("sse");
    }

    @Test
    void send_broadcastsAlertEvent() {
        AlertNotification notification = new AlertNotification(
                "SEN-01", "environment", "WARNING",
                "AQI exceeds threshold", "tenant-a", 42L
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        channel.send(notification);

        verify(sseEmitterRegistry).broadcast(eq("alert"), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("sensorId")).isEqualTo("SEN-01");
        assertThat(payload.get("module")).isEqualTo("environment");
        assertThat(payload.get("severity")).isEqualTo("WARNING");
        assertThat(payload.get("message")).isEqualTo("AQI exceeds threshold");
        assertThat(payload.get("tenantId")).isEqualTo("tenant-a");
        assertThat(payload.get("alertId")).isEqualTo(42L);
    }

    @Test
    void send_nullFields_usesDefaults() {
        AlertNotification notification = new AlertNotification(
                null, null, null, null, null, null
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        channel.send(notification);

        verify(sseEmitterRegistry).broadcast(eq("alert"), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("sensorId")).isEqualTo("");
        assertThat(payload.get("module")).isEqualTo("");
        assertThat(payload.get("severity")).isEqualTo("");
        assertThat(payload.get("message")).isEqualTo("");
        assertThat(payload.get("tenantId")).isEqualTo("");
        assertThat(payload.get("alertId")).isEqualTo(0L);
    }
}
