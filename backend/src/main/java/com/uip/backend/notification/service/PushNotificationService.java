package com.uip.backend.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.notification.config.VapidConfig;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.Map;

/**
 * Sends push notifications via the Web Push protocol (RFC 8030)
 * to all active subscriptions matching the notification's tenantId.
 *
 * Handles:
 * - 410 Gone: deactivates the expired subscription
 * - 429 Too Many Requests: skips and logs
 * - Other errors: logged but do not block remaining subscriptions
 */
@Component
@Slf4j
public class PushNotificationService implements NotificationChannel {

    private final PushSubscriptionRepository repository;
    private final PushService pushService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public PushNotificationService(PushSubscriptionRepository repository,
                                   VapidConfig vapidConfig,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;

        PushService service = null;
        boolean pushEnabled = false;
        try {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            String publicKey = vapidConfig.getPublicKey();
            String privateKey = vapidConfig.getPrivateKey();
            String subject = vapidConfig.getSubject();

            if (isBlank(publicKey) || isBlank(privateKey) || isBlank(subject)) {
                log.warn("Web push disabled: VAPID configuration is missing. Configure notification.vapid.publicKey/privateKey/subject to enable push.");
            } else {
                service = new PushService(publicKey, privateKey, subject);
                pushEnabled = true;
                log.info("PushNotificationService initialized with VAPID subject={}", subject);
            }
        } catch (Exception e) {
            log.warn("Web push disabled: failed to initialize PushService: {}", e.getMessage());
        }

        this.pushService = service;
        this.enabled = pushEnabled;
    }

    @Override
    public void send(AlertNotification notification) {
        if (!enabled || pushService == null) {
            log.debug("Push notification skipped: web push channel is disabled");
            return;
        }
        if (notification.tenantId() == null || notification.tenantId().isBlank()) {
            log.warn("Push notification skipped: no tenantId in alert");
            return;
        }

        List<PushSubscription> subscriptions =
                repository.findByTenantIdAndActiveTrue(notification.tenantId());

        if (subscriptions.isEmpty()) {
            log.debug("No active push subscriptions for tenant={}", notification.tenantId());
            return;
        }

        byte[] payload = buildPayload(notification);

        int sent = 0;
        int deactivated = 0;
        int failed = 0;

        for (PushSubscription sub : subscriptions) {
            if (sub.getP256dh() == null || sub.getAuthKey() == null) {
                log.debug("Skipping subscription id={}: missing encryption keys", sub.getId());
                continue;
            }
            try {
                Notification webPushNotification = new Notification(
                        sub.getEndpoint(),
                        sub.getP256dh(),
                        sub.getAuthKey(),
                        payload,
                        24 * 60 * 60 // TTL: 24 hours
                );

                HttpResponse response = pushService.send(webPushNotification);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 410) {
                    sub.setActive(false);
                    repository.save(sub);
                    deactivated++;
                    log.info("Push subscription expired (410 Gone): id={} endpoint={}",
                            sub.getId(), abbreviateEndpoint(sub.getEndpoint()));
                } else if (statusCode == 429) {
                    log.warn("Push service rate-limited (429) for subscription id={}", sub.getId());
                } else if (statusCode >= 200 && statusCode < 300) {
                    sent++;
                } else {
                    log.warn("Push send returned status={} for subscription id={}",
                            statusCode, sub.getId());
                }
            } catch (Exception e) {
                failed++;
                log.error("Push send failed for subscription id={} userId={}: {}",
                        sub.getId(), sub.getUserId(), e.getMessage());
            }
        }

        log.info("Push notification complete: tenant={} sensor={} sent={} deactivated={} failed={} of total={}",
                notification.tenantId(), notification.sensorId(),
                sent, deactivated, failed, subscriptions.size());
    }

    @Override
    public String getChannelName() {
        return "web-push";
    }

    private byte[] buildPayload(AlertNotification notification) {
        try {
            Map<String, Object> payload = Map.of(
                    "sensorId", notification.sensorId() != null ? notification.sensorId() : "",
                    "module", notification.module() != null ? notification.module() : "",
                    "severity", notification.severity() != null ? notification.severity() : "",
                    "message", notification.message() != null ? notification.message() : "",
                    "alertId", notification.alertId() != null ? notification.alertId() : 0L
            );
            return objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to serialize push payload", e);
            return "{\"error\":\"payload serialization failed\"}".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String abbreviateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() <= 40) {
            return endpoint;
        }
        return endpoint.substring(0, 40) + "...";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
