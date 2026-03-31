package com.uip.backend.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

/**
 * Subscribes to Redis channel "uip:alerts" and pushes messages to SSE clients.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService implements MessageListener {

    public static final String ALERT_CHANNEL = "uip:alerts";

    private final SseEmitterRegistry            sseEmitterRegistry;
    private final StringRedisTemplate           redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper                  objectMapper;

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, new PatternTopic(ALERT_CHANNEL));
        log.info("Subscribed to Redis channel: {}", ALERT_CHANNEL);
    }

    /**
     * Called by Redis when a message arrives on the channel.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody());
        log.debug("Redis alert received: {}", payload);
        try {
            Object data = objectMapper.readValue(payload, Object.class);
            sseEmitterRegistry.broadcast("alert", data);
        } catch (JsonProcessingException e) {
            // Broadcast raw string if JSON parsing fails
            sseEmitterRegistry.broadcast("alert", payload);
        }
    }

    /**
     * Publish an alert payload to Redis (called by AlertEngine or Kafka consumer).
     */
    public void publishAlert(Object alertPayload) {
        try {
            String json = objectMapper.writeValueAsString(alertPayload);
            redisTemplate.convertAndSend(ALERT_CHANNEL, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize alert for Redis publish", e);
        }
    }
}
