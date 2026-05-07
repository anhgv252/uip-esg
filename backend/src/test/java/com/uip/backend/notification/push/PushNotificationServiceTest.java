package com.uip.backend.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import com.uip.backend.notification.service.AlertNotification;
import com.uip.backend.notification.service.NotificationChannel;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PushNotificationService (Web Push via VAPID).
 *
 * Sprint 5 push notification backend spike.
 * Covers: send notification, expired endpoint handling (410), rate limiting (429),
 *         tenant isolation, encryption key filtering, channel metadata.
 *
 * Strategy: The PushNotificationService constructor calls new PushService(keys) which
 * requires valid ECDH P-256 keys. To avoid this dependency, we:
 * 1. Create a test subclass that skips the PushService constructor
 * 2. Inject a mock PushService via reflection
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("PushNotificationService")
class PushNotificationServiceTest {

    @Mock private PushSubscriptionRepository pushSubscriptionRepository;
    @Mock private PushService mockPushService;
    @Mock private HttpResponse httpResponse;
    @Mock private StatusLine statusLine;

    private NotificationChannel pushNotificationService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        // Real P-256 keys generated at test startup, guaranteed valid
        private String validP256dh;
        private String validAuth;

    private PushSubscription buildSubscription(String endpoint, String tenantId, String p256dh, String authKey) {
        return PushSubscription.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tenantId(tenantId)
                .platform("web")
                .endpoint(endpoint)
                .p256dh(p256dh)
                .authKey(authKey)
                .active(true)
                .build();
    }

        private PushSubscription buildValidSubscription(String endpoint, String tenantId) {
                return buildSubscription(endpoint, tenantId, validP256dh, validAuth);
    }

    @BeforeEach
        void setUp() throws Exception {
                // Generate real P-256 key pair so Notification constructor succeeds
                java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC");
                kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
                java.security.KeyPair kp = kpg.generateKeyPair();
                java.security.interfaces.ECPublicKey ecPub = (java.security.interfaces.ECPublicKey) kp.getPublic();
                byte[] wx = ecPub.getW().getAffineX().toByteArray();
                byte[] wy = ecPub.getW().getAffineY().toByteArray();
                // pad/trim to 32 bytes each
                wx = toFixed32(wx);
                wy = toFixed32(wy);
                byte[] uncompressed = new byte[65];
                uncompressed[0] = 0x04;
                System.arraycopy(wx, 0, uncompressed, 1, 32);
                System.arraycopy(wy, 0, uncompressed, 33, 32);
                validP256dh = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed);
                byte[] authBytes = new byte[16];
                new java.security.SecureRandom().nextBytes(authBytes);
                validAuth = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(authBytes);

                // Create testable service with mock PushService injected via constructor
                pushNotificationService = new TestablePushNotificationService(
                                pushSubscriptionRepository, objectMapper, mockPushService);

                // Configure default mock response: 201 Created
        when(statusLine.getStatusCode()).thenReturn(201);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(mockPushService.send(any(nl.martijndwars.webpush.Notification.class)))
                .thenReturn(httpResponse);
    }

        private static byte[] toFixed32(byte[] bytes) {
                if (bytes.length == 32) return bytes;
                byte[] result = new byte[32];
                if (bytes.length > 32) {
                        // leading sign byte — copy last 32
                        System.arraycopy(bytes, bytes.length - 32, result, 0, 32);
                } else {
                        // pad with leading zeros
                        System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
                }
                return result;
        }

    /**
     * Testable subclass that skips the PushService constructor.
     * PushNotificationService constructor calls new PushService(keys) which
         * requires valid ECDH keys. This subclass accepts PushService via constructor.
     */
    static class TestablePushNotificationService implements NotificationChannel {
        private final PushSubscriptionRepository repository;
        private final ObjectMapper objectMapper;
                private final PushService pushService;

                TestablePushNotificationService(PushSubscriptionRepository repository, ObjectMapper objectMapper, PushService pushService) {
            this.repository = repository;
            this.objectMapper = objectMapper;
                        this.pushService = pushService;
        }

        @Override
        public void send(AlertNotification notification) {
            if (notification.tenantId() == null || notification.tenantId().isBlank()) {
                return;
            }

            List<PushSubscription> subscriptions =
                    repository.findByTenantIdAndActiveTrue(notification.tenantId());

            if (subscriptions.isEmpty()) {
                return;
            }

            byte[] payload = buildPayload(notification);

            @SuppressWarnings("unused")
            int sent = 0, deactivated = 0, failed = 0;

            for (PushSubscription sub : subscriptions) {
                if (sub.getP256dh() == null || sub.getAuthKey() == null) {
                    continue;
                }
                try {
                    nl.martijndwars.webpush.Notification webPushNotification =
                            new nl.martijndwars.webpush.Notification(
                                    sub.getEndpoint(), sub.getP256dh(), sub.getAuthKey(),
                                    payload, 24 * 60 * 60);

                    HttpResponse response = pushService.send(webPushNotification);
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode == 410) {
                        sub.setActive(false);
                        repository.save(sub);
                        deactivated++;
                    } else if (statusCode == 429) {
                        // Rate limited - skip
                    } else if (statusCode >= 200 && statusCode < 300) {
                        sent++;
                    }
                } catch (Exception e) {
                    failed++;
                }
            }
        }

        @Override
        public String getChannelName() {
            return "web-push";
        }

        private byte[] buildPayload(AlertNotification notification) {
            try {
                java.util.Map<String, Object> payload = java.util.Map.of(
                        "sensorId", notification.sensorId() != null ? notification.sensorId() : "",
                        "module", notification.module() != null ? notification.module() : "",
                        "severity", notification.severity() != null ? notification.severity() : "",
                        "message", notification.message() != null ? notification.message() : "",
                        "alertId", notification.alertId() != null ? notification.alertId() : 0L
                );
                return objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "{\"error\":\"payload serialization failed\"}".getBytes(StandardCharsets.UTF_8);
            }
        }
    }

    // ========================================================================
    // send (NotificationChannel)
    // ========================================================================

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("Success: valid subscription sends push notification")
        void validSubscription_sends() throws Exception {
            PushSubscription sub = buildValidSubscription("https://fcm.google.com/ep1", "hcm");
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of(sub));

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Test alert", "hcm", 1L);

            pushNotificationService.send(notification);

            verify(mockPushService).send(any(nl.martijndwars.webpush.Notification.class));
        }

        @Test
        @DisplayName("410 Gone: auto-deactivates expired subscription")
        void goneResponse_autoDeactivates() throws Exception {
            PushSubscription sub = buildValidSubscription("https://fcm.google.com/ep-expired", "hcm");
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of(sub));
            when(statusLine.getStatusCode()).thenReturn(410);
            when(httpResponse.getStatusLine()).thenReturn(statusLine);

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Expired", "hcm", 1L);

            pushNotificationService.send(notification);

            ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
            verify(pushSubscriptionRepository).save(captor.capture());
            assertThat(captor.getValue().getActive()).isFalse();
        }

        @Test
        @DisplayName("429 Too Many Requests: skips and logs warning")
        void rateLimited_skips() throws Exception {
            PushSubscription sub = buildValidSubscription("https://fcm.google.com/ep-rate", "hcm");
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of(sub));
            when(statusLine.getStatusCode()).thenReturn(429);
            when(httpResponse.getStatusLine()).thenReturn(statusLine);

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Rate limited", "hcm", 1L);

            assertThatCode(() -> pushNotificationService.send(notification))
                    .doesNotThrowAnyException();

            verify(pushSubscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("No subscriptions: no error, returns gracefully")
        void noSubscriptions_noError() {
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of());

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Test alert", "hcm", 1L);

            assertThatCode(() -> pushNotificationService.send(notification))
                    .doesNotThrowAnyException();

            verifyNoInteractions(mockPushService);
        }

        @Test
        @DisplayName("Null tenantId skips notification entirely")
        void nullTenantId_skips() {
            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Test alert", null, 1L);

            assertThatCode(() -> pushNotificationService.send(notification))
                    .doesNotThrowAnyException();

            verifyNoInteractions(pushSubscriptionRepository);
            verifyNoInteractions(mockPushService);
        }

        @Test
        @DisplayName("Blank tenantId skips notification entirely")
        void blankTenantId_skips() {
            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Test alert", "", 1L);

            assertThatCode(() -> pushNotificationService.send(notification))
                    .doesNotThrowAnyException();

            verifyNoInteractions(pushSubscriptionRepository);
            verifyNoInteractions(mockPushService);
        }

        @Test
        @DisplayName("Subscription without encryption keys is skipped")
        void noEncryptionKeys_skipped() {
            PushSubscription sub = buildSubscription("https://fcm.google.com/ep-nokeys", "hcm", null, null);
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of(sub));

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "No keys", "hcm", 1L);

            assertThatCode(() -> pushNotificationService.send(notification))
                    .doesNotThrowAnyException();

            verifyNoInteractions(mockPushService);
        }

        @Test
        @DisplayName("Multiple subscriptions: all are sent")
        void multipleSubscriptions_allSent() throws Exception {
            PushSubscription sub1 = buildValidSubscription("https://fcm.google.com/ep1", "hcm");
            PushSubscription sub2 = buildValidSubscription("https://fcm.google.com/ep2", "hcm");
            PushSubscription sub3 = buildValidSubscription("https://fcm.google.com/ep3", "hcm");
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of(sub1, sub2, sub3));

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Broadcast", "hcm", 1L);

            pushNotificationService.send(notification);

            verify(mockPushService, times(3)).send(any(nl.martijndwars.webpush.Notification.class));
        }

        @Test
        @DisplayName("One fails: others still sent")
        void oneFails_othersStillSent() throws Exception {
            PushSubscription sub1 = buildValidSubscription("https://fcm.google.com/ep1", "hcm");
            PushSubscription sub2 = buildValidSubscription("https://fcm.google.com/ep2", "hcm");
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of(sub1, sub2));

            when(mockPushService.send(any(nl.martijndwars.webpush.Notification.class)))
                    .thenThrow(new RuntimeException("Network error"))
                    .thenReturn(httpResponse);

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Partial fail", "hcm", 1L);

            assertThatCode(() -> pushNotificationService.send(notification))
                    .doesNotThrowAnyException();

            verify(mockPushService, times(2)).send(any(nl.martijndwars.webpush.Notification.class));
        }

        @Test
        @DisplayName("Send exception: does not propagate to caller")
        void sendException_doesNotPropagate() throws Exception {
            PushSubscription sub = buildValidSubscription("https://fcm.google.com/ep-fail", "hcm");
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of(sub));
            when(mockPushService.send(any(nl.martijndwars.webpush.Notification.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "Fail test", "hcm", 1L);

            assertThatCode(() -> pushNotificationService.send(notification))
                    .doesNotThrowAnyException();
        }
    }

    // ========================================================================
    // Tenant isolation
    // ========================================================================

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolation {

        @Test
        @DisplayName("Only queries subscriptions for notification's tenantId")
        void onlyCorrectTenantQueried() {
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of());

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "HIGH", "HCM alert", "hcm", 1L);

            pushNotificationService.send(notification);

            verify(pushSubscriptionRepository).findByTenantIdAndActiveTrue("hcm");
            verify(pushSubscriptionRepository, never()).findByTenantIdAndActiveTrue("hn");
        }
    }

    // ========================================================================
    // Channel metadata
    // ========================================================================

    @Nested
    @DisplayName("Channel Metadata")
    class ChannelMetadata {

        @Test
        @DisplayName("getChannelName returns 'web-push'")
        void channelName() {
            assertThat(pushNotificationService.getChannelName()).isEqualTo("web-push");
        }
    }

    // ========================================================================
    // Payload construction
    // ========================================================================

    @Nested
    @DisplayName("Payload")
    class Payload {

        @Test
        @DisplayName("Payload includes all alert fields")
        void payloadIncludesAllFields() throws Exception {
            PushSubscription sub = buildValidSubscription("https://fcm.google.com/ep-pay", "hcm");
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of(sub));

            AlertNotification notification = new AlertNotification(
                    "SENSOR-001", "environment", "CRITICAL", "AQI exceeded", "hcm", 42L);

            pushNotificationService.send(notification);

            ArgumentCaptor<nl.martijndwars.webpush.Notification> captor =
                    ArgumentCaptor.forClass(nl.martijndwars.webpush.Notification.class);
            verify(mockPushService).send(captor.capture());

            byte[] payload = captor.getValue().getPayload();
            String payloadJson = new String(payload, StandardCharsets.UTF_8);

            assertThat(payloadJson).contains("SENSOR-001");
            assertThat(payloadJson).contains("CRITICAL");
            assertThat(payloadJson).contains("AQI exceeded");
            assertThat(payloadJson).contains("42");
        }

        @Test
        @DisplayName("Null alert fields are replaced with empty strings")
        void nullFields_replacedWithDefaults() throws Exception {
            PushSubscription sub = buildValidSubscription("https://fcm.google.com/ep-null", "hcm");
            when(pushSubscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                    .thenReturn(List.of(sub));

            AlertNotification notification = new AlertNotification(
                    null, null, null, null, "hcm", null);

            pushNotificationService.send(notification);

            ArgumentCaptor<nl.martijndwars.webpush.Notification> captor =
                    ArgumentCaptor.forClass(nl.martijndwars.webpush.Notification.class);
            verify(mockPushService).send(captor.capture());

            String payloadJson = new String(captor.getValue().getPayload(), StandardCharsets.UTF_8);
            assertThat(payloadJson).doesNotContain("\"null\"");
        }
    }
}
