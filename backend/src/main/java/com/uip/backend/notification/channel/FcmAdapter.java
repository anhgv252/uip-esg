package com.uip.backend.notification.channel;

import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import com.uip.backend.notification.service.AlertNotification;
import com.uip.backend.notification.service.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * S6-M04 — FCM (Firebase Cloud Messaging) notification channel.
 * Only active when push.fcm.enabled=true in configuration.
 * No-op otherwise — safe for environments without Firebase credentials.
 */
@Component
@ConditionalOnProperty(name = "push.fcm.enabled", havingValue = "true")
@Slf4j
public class FcmAdapter implements NotificationChannel {

    private final PushSubscriptionRepository subscriptionRepository;

    public FcmAdapter(PushSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
        log.info("FCM notification channel ENABLED");
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

        for (PushSubscription subscription : fcmTokens) {
            try {
                sendFcmMessage(subscription.getDeviceToken(), notification);
            } catch (Exception e) {
                log.warn("FCM send failed for tenant={}: {}", notification.tenantId(), e.getMessage());
                log.debug("Failed FCM token: {}", maskToken(subscription.getDeviceToken()));
                handleInvalidToken(subscription, e);
            }
        }
    }

    @Override
    public String getChannelName() {
        return "fcm";
    }

    /**
     * Send FCM message. In production, this uses FirebaseMessaging.getInstance().send().
     * For now, logs the notification — actual Firebase integration requires service account key.
     */
    private void sendFcmMessage(String token, AlertNotification notification) {
        log.debug("FCM push sent: token={} severity={} module={} sensor={}",
                maskToken(token), notification.severity(), notification.module(), notification.sensorId());
        // TODO: Wire FirebaseMessaging when service account key is configured
        // Message message = Message.builder()
        //     .setToken(token)
        //     .setNotification(Notification.builder()
        //         .setTitle("Alert: " + notification.severity())
        //         .setBody(notification.message())
        //         .build())
        //     .putData("alertId", String.valueOf(notification.alertId()))
        //     .putData("module", notification.module())
        //     .build();
        // FirebaseMessaging.getInstance().send(message);
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
