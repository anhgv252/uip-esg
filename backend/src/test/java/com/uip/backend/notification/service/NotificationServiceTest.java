package com.uip.backend.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock private SseEmitterRegistry            sseEmitterRegistry;
    @Mock private StringRedisTemplate           redisTemplate;
    @Mock private RedisMessageListenerContainer listenerContainer;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks private NotificationService notificationService;

    // ─── publishAlert ────────────────────────────────────────────────────────

    @Test
    @DisplayName("publishAlert: serializes payload and sends to Redis channel")
    void publishAlert_validPayload_publishesToRedis() {
        Map<String, Object> alert = Map.of("sensorId", "ENV-001", "severity", "CRITICAL");

        notificationService.publishAlert(alert);

        verify(redisTemplate).convertAndSend(
                eq(NotificationService.ALERT_CHANNEL),
                argThat((String json) -> json.contains("ENV-001") && json.contains("CRITICAL")));
    }

    @Test
    @DisplayName("publishAlert: string payload is also published")
    void publishAlert_stringPayload_publishedDirectly() {
        notificationService.publishAlert("raw-alert-data");

        verify(redisTemplate).convertAndSend(
                eq(NotificationService.ALERT_CHANNEL),
                contains("raw-alert-data"));
    }

    // ─── onMessage ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("onMessage: valid JSON is parsed and broadcast")
    void onMessage_validJson_broadcastsToClients() {
        String json = "{\"sensorId\":\"ENV-002\",\"severity\":\"WARNING\"}";
        Message message = mockMessage(json);

        notificationService.onMessage(message, null);

        verify(sseEmitterRegistry).broadcast(eq("alert"), any());
    }

    @Test
    @DisplayName("onMessage: malformed JSON is broadcast as raw string")
    void onMessage_invalidJson_broadcastsRawString() {
        String bad = "not-json";
        Message message = mockMessage(bad);

        notificationService.onMessage(message, null);

        verify(sseEmitterRegistry).broadcast("alert", bad);
    }

    // ─── subscribe (@PostConstruct) ──────────────────────────────────────────

    @Test
    @DisplayName("subscribe: registers NotificationService as listener on ALERT_CHANNEL")
    void subscribe_registersRedisListener() {
        notificationService.subscribe();

        verify(listenerContainer).addMessageListener(
                eq(notificationService),
                (org.springframework.data.redis.listener.Topic)
                        argThat(topic -> topic instanceof PatternTopic));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Message mockMessage(String body) {
        Message msg = mock(Message.class);
        when(msg.getBody()).thenReturn(body.getBytes());
        return msg;
    }
}
