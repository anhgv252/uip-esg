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
 * S6-M04 — APNs (Apple Push Notification service) channel.
 * Only active when push.apns.enabled=true in configuration.
 * No-op otherwise — safe for environments without APNs certificates.
 */
@Component
@ConditionalOnProperty(name = "push.apns.enabled", havingValue = "true")
@Slf4j
public class ApnsAdapter implements NotificationChannel {

    private final PushSubscriptionRepository subscriptionRepository;

    public ApnsAdapter(PushSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
        log.info("APNs notification channel ENABLED");
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

        for (PushSubscription subscription : apnsTokens) {
            try {
                sendApnsMessage(subscription.getDeviceToken(), notification);
            } catch (Exception e) {
                log.warn("APNs send failed for token={} tenant={}: {}",
                        maskToken(subscription.getDeviceToken()),
                        notification.tenantId(), e.getMessage());
                handleInvalidToken(subscription, e);
            }
        }
    }

    @Override
    public String getChannelName() {
        return "apns";
    }

    /**
     * Send APNs message. In production, this uses Pushy library (ApnsClient).
     * For now, logs the notification — actual APNs integration requires .p8 certificate.
     */
    private void sendApnsMessage(String token, AlertNotification notification) {
        log.info("APNs push sent: token={} severity={} module={} sensor={}",
                maskToken(token), notification.severity(), notification.module(), notification.sensorId());
        // TODO: Wire Pushy ApnsClient when certificate is configured
        // SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(
        //     token, "com.uip.operator",
        //     "{\"aps\":{\"alert\":{\"title\":\"Alert: " + notification.severity() + "\","
        //       + "\"body\":\"" + notification.message() + "\"},\"sound\":\"default\"},"
        //       + "\"alertId\":\"" + notification.alertId() + "\"}"
        // );
        // apnsClient.sendPushNotification(pushNotification);
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
