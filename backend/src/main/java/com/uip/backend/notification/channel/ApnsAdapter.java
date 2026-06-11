package com.uip.backend.notification.channel;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import com.uip.backend.notification.service.AlertNotification;
import com.uip.backend.notification.service.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * S6-M04 — APNs (Apple Push Notification service) channel via Pushy library.
 * Only active when push.apns.enabled=true in configuration.
 * If certificate is not configured, operates in graceful no-op mode.
 */
@Component
@ConditionalOnProperty(name = "push.apns.enabled", havingValue = "true")
@Slf4j
public class ApnsAdapter implements NotificationChannel {

    private final PushSubscriptionRepository subscriptionRepository;
    private final ApnsClient apnsClient;
    private final String topic;
    private final ObjectMapper objectMapper;
    private final boolean initialized;

    /**
     * Production constructor — initializes Pushy ApnsClient from PKCS12 certificate.
     */
    public ApnsAdapter(
            PushSubscriptionRepository subscriptionRepository,
            @Value("${uip.push.apns.certificate-path:}") String certificatePath,
            @Value("${uip.push.apns.certificate-password:}") String certificatePassword,
            @Value("${uip.push.apns.topic:com.uip.operator}") String topic,
            @Value("${uip.push.apns.production:false}") boolean production
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.topic = topic;
        this.objectMapper = new ObjectMapper();
        this.apnsClient = initializeApnsClient(certificatePath, certificatePassword, production);
        this.initialized = this.apnsClient != null;

        if (initialized) {
            log.info("APNs notification channel ENABLED — ApnsClient ready, topic={}", topic);
        } else {
            log.warn("APNs notification channel ENABLED but no certificate configured — operating in no-op mode");
        }
    }

    /**
     * Test constructor — accepts a pre-built ApnsClient instance.
     */
    ApnsAdapter(PushSubscriptionRepository subscriptionRepository,
                ApnsClient apnsClient, String topic) {
        this.subscriptionRepository = subscriptionRepository;
        this.apnsClient = apnsClient;
        this.topic = topic;
        this.objectMapper = new ObjectMapper();
        this.initialized = apnsClient != null;
    }

    @Override
    public void send(AlertNotification notification) {
        List<PushSubscription> apnsTokens = subscriptionRepository
                .findByTenantIdAndActiveTrue(notification.tenantId())
                .stream()
                .filter(sub -> "apns".equalsIgnoreCase(sub.getPlatform()) && sub.getDeviceToken() != null)
                .toList();

        if (apnsTokens.isEmpty()) {
            log.debug("No APNs tokens found for tenant={}", notification.tenantId());
            return;
        }

        if (!initialized) {
            log.debug("APNs not initialized — skipping {} tokens for tenant={}",
                    apnsTokens.size(), notification.tenantId());
            return;
        }

        for (PushSubscription subscription : apnsTokens) {
            try {
                sendApnsMessage(subscription.getDeviceToken(), notification);
            } catch (Exception e) {
                log.warn("APNs send failed for tenant={} token={}: {}",
                        notification.tenantId(), maskToken(subscription.getDeviceToken()), e.getMessage());
                handleInvalidToken(subscription, e);
            }
        }
    }

    @Override
    public String getChannelName() {
        return "apns";
    }

    /**
     * Send APNs push notification via Pushy ApnsClient.
     * Builds an APS payload with alert title/body and custom data fields.
     */
    private void sendApnsMessage(String deviceToken, AlertNotification notification) throws Exception {
        String payload = buildPayload(notification);

        SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(
                deviceToken,
                topic,
                payload
        );

        PushNotificationFuture<SimpleApnsPushNotification,
                PushNotificationResponse<SimpleApnsPushNotification>> future =
                apnsClient.sendNotification(pushNotification);

        PushNotificationResponse<SimpleApnsPushNotification> response = future.get();

        if (response.isAccepted()) {
            log.debug("APNs push sent: token={} severity={} module={} sensor={}",
                    maskToken(deviceToken), notification.severity(),
                    notification.module(), notification.sensorId());
        } else {
            String rejectionReason = response.getRejectionReason().orElse("UNKNOWN");
            throw new RuntimeException("APNs rejected: " + rejectionReason);
        }
    }

    /**
     * Build APNs JSON payload with alert content and custom data.
     */
    private String buildPayload(AlertNotification notification) {
        Map<String, Object> aps = new LinkedHashMap<>();
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("title", "Alert: " + notification.severity());
        alert.put("body", notification.message());
        aps.put("alert", alert);
        aps.put("sound", "default");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("aps", aps);
        payload.put("module", String.valueOf(notification.module()));
        payload.put("severity", String.valueOf(notification.severity()));

        if (notification.sensorId() != null) {
            payload.put("sensorId", notification.sensorId());
        }
        if (notification.alertId() != null) {
            payload.put("alertId", String.valueOf(notification.alertId()));
        }
        if (notification.tenantId() != null) {
            payload.put("tenantId", notification.tenantId());
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize APNs payload: {}", e.getMessage());
            return "{\"aps\":{\"alert\":{\"title\":\"Alert\",\"body\":\"" +
                    notification.message().replace("\"", "'") + "\"},\"sound\":\"default\"}}";
        }
    }

    /**
     * Initialize Pushy ApnsClient from PKCS12 certificate file.
     * Returns null if certificate path is not configured or invalid — graceful no-op.
     */
    private ApnsClient initializeApnsClient(String certificatePath, String certificatePassword, boolean production) {
        if (certificatePath == null || certificatePath.isBlank()) {
            return null;
        }

        if (!Files.exists(Path.of(certificatePath))) {
            log.warn("APNs certificate file not found: {}", certificatePath);
            return null;
        }

        try {
            ApnsClientBuilder builder = new ApnsClientBuilder()
                    .setClientCredentials(new FileInputStream(certificatePath), certificatePassword);

            if (production) {
                builder.setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST);
            } else {
                builder.setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST);
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("Failed to initialize APNs client from certificate: {}", e.getMessage());
            return null;
        }
    }

    private void handleInvalidToken(PushSubscription subscription, Exception error) {
        String msg = error.getMessage();
        if (msg != null && (msg.contains("BadDeviceToken") || msg.contains("Unregistered"))) {
            log.warn("Auto-cleanup invalid APNs token: id={}", subscription.getId());
            subscription.setActive(false);
            subscriptionRepository.save(subscription);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
