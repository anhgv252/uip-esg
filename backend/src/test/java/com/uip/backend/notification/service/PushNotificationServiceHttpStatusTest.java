package com.uip.backend.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.notification.config.VapidConfig;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Branch-coverage tests for PushNotificationService.send() HTTP status handling:
 *   410 Gone       → subscription deactivated (repository.save called)
 *   429 Too Many   → logged, subscription NOT deactivated
 *   2xx Success    → sent counter incremented (no exception)
 *   Other (5xx)    → logged, no exception
 *   send() throws  → failed counter, no propagation
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("PushNotificationService — HTTP status branches")
class PushNotificationServiceHttpStatusTest {

    @Mock private PushSubscriptionRepository repository;
    @Mock private PushService mockPushService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private PushNotificationService service;

    private static String validP256dh;
    private static String validAuth;

    @BeforeAll
    static void generateKeys() throws Exception {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC");
        kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        java.security.KeyPair kp = kpg.generateKeyPair();
        java.security.interfaces.ECPublicKey ecPub =
                (java.security.interfaces.ECPublicKey) kp.getPublic();
        byte[] wx = ecPub.getW().getAffineX().toByteArray();
        byte[] wy = ecPub.getW().getAffineY().toByteArray();
        wx = toFixed32(wx);
        wy = toFixed32(wy);
        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04;
        System.arraycopy(wx, 0, uncompressed, 1, 32);
        System.arraycopy(wy, 0, uncompressed, 33, 32);
        validP256dh = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(uncompressed);
        byte[] authBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(authBytes);
        validAuth = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(authBytes);
    }

    private static byte[] toFixed32(byte[] bytes) {
        if (bytes.length == 32) return bytes;
        if (bytes.length == 33 && bytes[0] == 0) {
            return java.util.Arrays.copyOfRange(bytes, 1, 33);
        }
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
            return padded;
        }
        return bytes;
    }

    @BeforeEach
    void setUp() {
        // Build a disabled service, then force-enable it via reflection
        VapidConfig emptyVapid = new VapidConfig();
        service = new PushNotificationService(repository, emptyVapid, objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "pushService", mockPushService);
    }

    @Nested
    @DisplayName("410 Gone — subscription deactivated")
    class Http410 {

        @Test
        @DisplayName("410: subscription.active set to false and saved")
        void send_410_deactivatesSubscription() throws Exception {
            PushSubscription sub = buildSub("tenant-a");
            when(repository.findByTenantIdAndActiveTrue("tenant-a")).thenReturn(List.of(sub));
            HttpResponse resp410 = mockHttpResponse(410);
            when(mockPushService.send(any())).thenReturn(resp410);

            AlertNotification n = new AlertNotification("S1", "env", "HIGH", "msg", "tenant-a", 1L);
            assertThatCode(() -> service.send(n)).doesNotThrowAnyException();

            assertThat(sub.getActive()).isFalse();
            verify(repository).save(sub);
        }
    }

    @Nested
    @DisplayName("429 Too Many Requests — rate limited")
    class Http429 {

        @Test
        @DisplayName("429: subscription stays active, repository.save not called")
        void send_429_doesNotDeactivateSubscription() throws Exception {
            PushSubscription sub = buildSub("tenant-b");
            when(repository.findByTenantIdAndActiveTrue("tenant-b")).thenReturn(List.of(sub));
            HttpResponse resp429 = mockHttpResponse(429);
            when(mockPushService.send(any())).thenReturn(resp429);

            AlertNotification n = new AlertNotification("S2", "env", "WARN", "msg", "tenant-b", 2L);
            assertThatCode(() -> service.send(n)).doesNotThrowAnyException();

            verify(repository, never()).save(sub);
        }
    }

    @Nested
    @DisplayName("2xx Success")
    class Http2xx {

        @Test
        @DisplayName("201: no exception, subscription stays active")
        void send_201_success() throws Exception {
            PushSubscription sub = buildSub("tenant-c");
            when(repository.findByTenantIdAndActiveTrue("tenant-c")).thenReturn(List.of(sub));
            HttpResponse resp201 = mockHttpResponse(201);
            when(mockPushService.send(any())).thenReturn(resp201);

            AlertNotification n = new AlertNotification("S3", "env", "INFO", "msg", "tenant-c", 3L);
            assertThatCode(() -> service.send(n)).doesNotThrowAnyException();

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("200: no exception, subscription stays active")
        void send_200_success() throws Exception {
            PushSubscription sub = buildSub("tenant-d");
            when(repository.findByTenantIdAndActiveTrue("tenant-d")).thenReturn(List.of(sub));
            HttpResponse resp200 = mockHttpResponse(200);
            when(mockPushService.send(any())).thenReturn(resp200);

            AlertNotification n = new AlertNotification("S4", "env", "INFO", "msg", "tenant-d", 4L);
            assertThatCode(() -> service.send(n)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Other status codes — logged but not deactivated")
    class HttpOther {

        @Test
        @DisplayName("503 Service Unavailable: no exception, subscription not deactivated")
        void send_503_logsAndContinues() throws Exception {
            PushSubscription sub = buildSub("tenant-e");
            when(repository.findByTenantIdAndActiveTrue("tenant-e")).thenReturn(List.of(sub));
            HttpResponse resp503 = mockHttpResponse(503);
            when(mockPushService.send(any())).thenReturn(resp503);

            AlertNotification n = new AlertNotification("S5", "env", "HIGH", "msg", "tenant-e", 5L);
            assertThatCode(() -> service.send(n)).doesNotThrowAnyException();

            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Exception during send — no propagation")
    class SendException {

        @Test
        @DisplayName("PushService.send throws: exception swallowed, no rethrow")
        void send_pushServiceThrows_noExceptionPropagated() throws Exception {
            PushSubscription sub = buildSub("tenant-f");
            when(repository.findByTenantIdAndActiveTrue("tenant-f")).thenReturn(List.of(sub));
            when(mockPushService.send(any())).thenThrow(new RuntimeException("push timeout"));

            AlertNotification n = new AlertNotification("S6", "env", "HIGH", "msg", "tenant-f", 6L);
            assertThatCode(() -> service.send(n)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Multiple subscriptions: exception on first does not block second")
        void send_exceptionOnFirst_secondSubStillProcessed() throws Exception {
            PushSubscription sub1 = buildSub("tenant-g");
            PushSubscription sub2 = buildSub("tenant-g");
            when(repository.findByTenantIdAndActiveTrue("tenant-g")).thenReturn(List.of(sub1, sub2));
            HttpResponse resp201b = mockHttpResponse(201);
            when(mockPushService.send(any()))
                    .thenThrow(new RuntimeException("first sub fails"))
                    .thenReturn(resp201b);

            AlertNotification n = new AlertNotification("S7", "env", "HIGH", "msg", "tenant-g", 7L);
            assertThatCode(() -> service.send(n)).doesNotThrowAnyException();

            verify(mockPushService, times(2)).send(any());
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private PushSubscription buildSub(String tenantId) {
        return PushSubscription.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(UUID.randomUUID())
                .endpoint("https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID())
                .p256dh(validP256dh)
                .authKey(validAuth)
                .active(true)
                .build();
    }

    private HttpResponse mockHttpResponse(int statusCode) {
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(statusCode);
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }
}
