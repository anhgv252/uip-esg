package com.uip.backend.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.notification.config.VapidConfig;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import nl.martijndwars.webpush.PushService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for the actual PushNotificationService class (not TestablePushNotificationService).
 * Focuses on the disabled path (no VAPID config) and the enabled + send flow.
 */
@ExtendWith(MockitoExtension.class)
class PushNotificationServiceDirectTest {

    @Mock PushSubscriptionRepository repository;
    @Mock PushService mockPushService;

    private VapidConfig emptyVapid;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        emptyVapid = new VapidConfig();
        // leave publicKey/privateKey/subject null → disabled path
    }

    @Test
    void constructor_withEmptyVapid_disablesService() {
        PushNotificationService service = new PushNotificationService(repository, emptyVapid, objectMapper);
        // Should NOT throw; service initialized in disabled state
        assertThatCode(() -> service.send(new AlertNotification(
                "SEN-01", "env", "HIGH", "msg", "tenant-a", 1L)))
                .doesNotThrowAnyException();
        verifyNoInteractions(repository);
    }

    @Test
    void send_whenDisabled_skipsAllLogic() {
        PushNotificationService service = new PushNotificationService(repository, emptyVapid, objectMapper);
        AlertNotification n = new AlertNotification("SEN-02", "env", "WARNING", "test", "t1", 2L);

        service.send(n);

        verifyNoInteractions(repository);
    }

    @Test
    void channelName_isWebPush() {
        PushNotificationService service = new PushNotificationService(repository, emptyVapid, objectMapper);
        assertThat(service.getChannelName()).isEqualTo("web-push");
    }

    @Test
    void constructor_withBlankStringVapid_disablesService() {
        VapidConfig blanks = new VapidConfig();
        blanks.setPublicKey("");
        blanks.setPrivateKey("");
        blanks.setSubject("");

        PushNotificationService service = new PushNotificationService(repository, blanks, objectMapper);
        assertThatCode(() -> service.send(new AlertNotification(
                "S", "m", "H", "msg", "t", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void send_whenEnabled_withNullTenantId_skips() throws Exception {
        // Build a service that is "enabled" via reflection to test the null-tenant guard
        PushNotificationService service = new PushNotificationService(repository, emptyVapid, objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "pushService", mockPushService);

        AlertNotification n = new AlertNotification("S", "m", "H", "msg", null, 1L);
        service.send(n);

        verifyNoInteractions(repository);
    }

    @Test
    void send_whenEnabled_withEmptyTenantId_skips() throws Exception {
        PushNotificationService service = new PushNotificationService(repository, emptyVapid, objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "pushService", mockPushService);

        AlertNotification n = new AlertNotification("S", "m", "H", "msg", "", 1L);
        service.send(n);

        verifyNoInteractions(repository);
    }

    @Test
    void send_whenEnabled_withNoSubscriptions_skips() throws Exception {
        PushNotificationService service = new PushNotificationService(repository, emptyVapid, objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "pushService", mockPushService);
        when(repository.findByTenantIdAndActiveTrue("t1")).thenReturn(List.of());

        AlertNotification n = new AlertNotification("S", "m", "H", "msg", "t1", 1L);
        service.send(n);

        verify(repository).findByTenantIdAndActiveTrue("t1");
    }

    @Test
    void send_whenEnabled_subscriptionMissingKeys_skips() throws Exception {
        PushNotificationService service = new PushNotificationService(repository, emptyVapid, objectMapper);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "pushService", mockPushService);

        PushSubscription sub = PushSubscription.builder()
                .id(UUID.randomUUID()).tenantId("t1").endpoint("https://ep")
                .p256dh(null).authKey(null).active(true).build();
        when(repository.findByTenantIdAndActiveTrue("t1")).thenReturn(List.of(sub));

        AlertNotification n = new AlertNotification("S", "m", "H", "msg", "t1", 1L);
        assertThatCode(() -> service.send(n)).doesNotThrowAnyException();
        verify(mockPushService, never()).send(any());
    }
}
