package com.uip.backend.notification.channel;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import com.uip.backend.notification.service.AlertNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * S6-M04 — FCM adapter unit tests.
 * Tests verify routing logic, token cleanup, and FirebaseMessaging integration.
 */
@ExtendWith(MockitoExtension.class)
class FcmAdapterTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    private FcmAdapter fcmAdapter;

    private final AlertNotification testNotification = new AlertNotification(
            "SENSOR-001", "FLOOD", "HIGH", "Water level rising", "hcm", 1L);

    @BeforeEach
    void setUp() {
        // Use package-private test constructor to inject mock FirebaseMessaging
        fcmAdapter = new FcmAdapter(subscriptionRepository, firebaseMessaging);
    }

    @Test
    void send_withNoFcmTokens_doesNotThrow() {
        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(Collections.emptyList());

        fcmAdapter.send(testNotification);

        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
        verifyNoMoreInteractions(subscriptionRepository);
    }

    @Test
    void send_withFcmTokens_processesAllTokens() throws Exception {
        PushSubscription sub1 = createFcmSubscription("token-1");
        PushSubscription sub2 = createFcmSubscription("token-2");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub1, sub2));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("msg-id-1", "msg-id-2");

        fcmAdapter.send(testNotification);

        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
        verify(firebaseMessaging, times(2)).send(any(Message.class));
    }

    @Test
    void send_filtersNonFcmPlatforms() throws Exception {
        PushSubscription webSub = PushSubscription.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).tenantId("hcm")
                .platform("web").endpoint("https://push.example.com/1").active(true)
                .build();
        PushSubscription fcmSub = createFcmSubscription("fcm-token-1");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(webSub, fcmSub));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("msg-id-1");

        fcmAdapter.send(testNotification);

        // Only FCM token should be sent, web platform filtered out
        verify(firebaseMessaging, times(1)).send(any(Message.class));
    }

    @Test
    void send_skipsSubscriptionWithoutDeviceToken() {
        PushSubscription noToken = PushSubscription.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).tenantId("hcm")
                .platform("fcm").endpoint("endpoint").deviceToken(null).active(true)
                .build();

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(noToken));

        fcmAdapter.send(testNotification);

        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
        verifyNoMoreInteractions(subscriptionRepository);
    }

    @Test
    void send_firebaseSendFailure_doesNotPropagate() throws Exception {
        PushSubscription sub = createFcmSubscription("valid-token");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));

        FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);
        when(mockException.getMessage()).thenReturn("NotRegistered");
        when(firebaseMessaging.send(any(Message.class))).thenThrow(mockException);

        // Should NOT throw — adapter handles internally
        assertDoesNotThrow(() -> fcmAdapter.send(testNotification));

        // Invalid token should be cleaned up
        verify(subscriptionRepository).save(sub);
        assertFalse(sub.getActive());
    }

    @Test
    void send_firebaseFailure_notInvalidToken_doesNotCleanup() throws Exception {
        PushSubscription sub = createFcmSubscription("valid-token");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));

        FirebaseMessagingException mockException = mock(FirebaseMessagingException.class);
        when(mockException.getMessage()).thenReturn("InternalServerError");
        when(firebaseMessaging.send(any(Message.class))).thenThrow(mockException);

        assertDoesNotThrow(() -> fcmAdapter.send(testNotification));

        // Should NOT deactivate — error is not token-related
        verify(subscriptionRepository, never()).save(any());
        assertTrue(sub.getActive());
    }

    @Test
    void send_noCredentials_gracefulNoOp() {
        // null FirebaseMessaging → not initialized, graceful no-op
        FcmAdapter noOpAdapter = new FcmAdapter(subscriptionRepository, (FirebaseMessaging) null);

        PushSubscription sub = createFcmSubscription("token-x");
        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));

        // Should not throw — graceful no-op when not initialized
        assertDoesNotThrow(() -> noOpAdapter.send(testNotification));
    }

    @Test
    void send_messageBuiltAndSent() throws Exception {
        PushSubscription sub = createFcmSubscription("token-abc");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("msg-id");

        fcmAdapter.send(testNotification);

        // Verify FirebaseMessaging.send() was called exactly once
        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void send_notificationWithNullFields_stillSends() throws Exception {
        AlertNotification minimalNotification = new AlertNotification(
                null, "ENV", "LOW", "All clear", "hcm", null);
        PushSubscription sub = createFcmSubscription("token-min");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("msg-id");

        assertDoesNotThrow(() -> fcmAdapter.send(minimalNotification));

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void getChannelName_returnsFcm() {
        assertEquals("fcm", fcmAdapter.getChannelName());
    }

    private PushSubscription createFcmSubscription(String token) {
        return PushSubscription.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tenantId("hcm")
                .platform("fcm")
                .endpoint("fcm://" + token)
                .deviceToken(token)
                .active(true)
                .build();
    }
}
