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

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Subscribes to Redis channel "uip:alerts" and routes messages
 * through NotificationRouter to all registered channels (SSE, Web Push, etc.).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService implements MessageListener {

    public static final String ALERT_CHANNEL = "uip:alerts";

    private final NotificationRouter          notificationRouter;
    private final StringRedisTemplate         redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper                objectMapper;

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, new PatternTopic(ALERT_CHANNEL));
        log.info("Subscribed to Redis channel: {}", ALERT_CHANNEL);
    }

    /**
     * Called by Redis when a message arrives on the channel.
     * Deserializes the payload into an AlertNotification and routes it.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        log.debug("Redis alert received: {}", payload);
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            AlertNotification notification = new AlertNotification(
                    (String) data.getOrDefault("sensorId", ""),
                    (String) data.getOrDefault("module", ""),
                    (String) data.getOrDefault("severity", ""),
                    (String) data.getOrDefault("message", ""),
                    (String) data.getOrDefault("tenantId", ""),
                    data.get("alertId") != null ? ((Number) data.get("alertId")).longValue() : null
            );
            notificationRouter.route(notification);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Redis alert payload, sending raw message: {}", e.getMessage());
            AlertNotification fallback = new AlertNotification(
                    "", "", "", payload, "", null);
            notificationRouter.route(fallback);
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
