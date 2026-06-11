package com.uip.backend.notification.channel;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import com.uip.backend.notification.service.AlertNotification;
import com.uip.backend.notification.service.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * S6-M04 — FCM (Firebase Cloud Messaging) notification channel.
 * Only active when push.fcm.enabled=true in configuration.
 * If service account key is not configured, operates in graceful no-op mode.
 */
@Component
@ConditionalOnProperty(name = "push.fcm.enabled", havingValue = "true")
@Slf4j
public class FcmAdapter implements NotificationChannel {

    private final PushSubscriptionRepository subscriptionRepository;
    private final FirebaseMessaging firebaseMessaging;
    private final boolean initialized;

    /**
     * Production constructor — initializes Firebase from service account key file.
     */
    public FcmAdapter(
            PushSubscriptionRepository subscriptionRepository,
            @Value("${uip.push.fcm.service-account-key:}") String serviceAccountKeyPath
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.firebaseMessaging = initializeFirebase(serviceAccountKeyPath);
        this.initialized = this.firebaseMessaging != null;

        if (initialized) {
            log.info("FCM notification channel ENABLED — FirebaseMessaging ready");
        } else {
            log.warn("FCM notification channel ENABLED but no service account key configured — operating in no-op mode");
        }
    }

    /**
     * Test constructor — accepts a pre-built FirebaseMessaging instance.
     */
    FcmAdapter(PushSubscriptionRepository subscriptionRepository, FirebaseMessaging firebaseMessaging) {
        this.subscriptionRepository = subscriptionRepository;
        this.firebaseMessaging = firebaseMessaging;
        this.initialized = firebaseMessaging != null;
    }

    @Override
    public void send(AlertNotification notification) {
        List<PushSubscription> fcmTokens = subscriptionRepository
                .findByTenantIdAndActiveTrue(notification.tenantId())
                .stream()
                .filter(sub -> "fcm".equalsIgnoreCase(sub.getPlatform()) && sub.getDeviceToken() != null)
                .toList();

        if (fcmTokens.isEmpty()) {
            log.debug("No FCM tokens found for tenant={}", notification.tenantId());
            return;
        }

        if (!initialized) {
            log.debug("FCM not initialized — skipping {} tokens for tenant={}",
                    fcmTokens.size(), notification.tenantId());
            return;
        }

        for (PushSubscription subscription : fcmTokens) {
            try {
                sendFcmMessage(subscription.getDeviceToken(), notification);
            } catch (Exception e) {
                log.warn("FCM send failed for tenant={} token={}: {}",
                        notification.tenantId(), maskToken(subscription.getDeviceToken()), e.getMessage());
                handleInvalidToken(subscription, e);
            }
        }
    }

    @Override
    public String getChannelName() {
        return "fcm";
    }

    /**
     * Send FCM message via FirebaseMessaging SDK.
     * Builds a Message with notification payload and alert metadata as data fields.
     */
    private void sendFcmMessage(String token, AlertNotification notification) throws FirebaseMessagingException {
        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle("Alert: " + notification.severity())
                        .setBody(notification.message())
                        .build())
                .putData("module", String.valueOf(notification.module()))
                .putData("severity", String.valueOf(notification.severity()));

        if (notification.sensorId() != null) {
            messageBuilder.putData("sensorId", notification.sensorId());
        }
        if (notification.alertId() != null) {
            messageBuilder.putData("alertId", String.valueOf(notification.alertId()));
        }
        if (notification.tenantId() != null) {
            messageBuilder.putData("tenantId", notification.tenantId());
        }

        String messageId = firebaseMessaging.send(messageBuilder.build());
        log.debug("FCM push sent: messageId={} token={} severity={} module={} sensor={}",
                messageId, maskToken(token), notification.severity(),
                notification.module(), notification.sensorId());
    }

    /**
     * Initialize FirebaseApp from service account key file.
     * Returns null if key file is not configured or invalid — graceful no-op.
     */
    private FirebaseMessaging initializeFirebase(String serviceAccountKeyPath) {
        if (serviceAccountKeyPath == null || serviceAccountKeyPath.isBlank()) {
            return null;
        }

        if (!Files.exists(Path.of(serviceAccountKeyPath))) {
            log.warn("FCM service account key file not found: {}", serviceAccountKeyPath);
            return null;
        }

        try (FileInputStream serviceAccountStream = new FileInputStream(serviceAccountKeyPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                    .build();

            // Check if FirebaseApp already initialized (e.g., in test contexts)
            FirebaseApp app;
            try {
                app = FirebaseApp.getInstance();
            } catch (IllegalStateException e) {
                app = FirebaseApp.initializeApp(options);
            }

            return FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            log.warn("Failed to initialize Firebase from key file: {}", e.getMessage());
            return null;
        }
    }

    private void handleInvalidToken(PushSubscription subscription, Exception error) {
        String msg = error.getMessage();
        if (msg != null && (msg.contains("NotRegistered") || msg.contains("invalid-registration-token"))) {
            log.warn("Auto-cleanup invalid FCM token: id={}", subscription.getId());
            subscription.setActive(false);
            subscriptionRepository.save(subscription);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
