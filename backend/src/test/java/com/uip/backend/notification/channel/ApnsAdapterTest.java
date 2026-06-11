package com.uip.backend.notification.channel;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsPushNotification;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import com.uip.backend.notification.service.AlertNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * S6-M04 — APNs adapter unit tests.
 * Tests verify routing logic, token cleanup, and Pushy ApnsClient integration.
 */
@ExtendWith(MockitoExtension.class)
class ApnsAdapterTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private ApnsClient apnsClient;

    @Mock
    private PushNotificationFuture<SimpleApnsPushNotification,
            PushNotificationResponse<SimpleApnsPushNotification>> mockFuture;

    @Mock
    private PushNotificationResponse<SimpleApnsPushNotification> mockResponse;

    @Captor
    private ArgumentCaptor<SimpleApnsPushNotification> notificationCaptor;

    private ApnsAdapter apnsAdapter;

    private final AlertNotification testNotification = new AlertNotification(
            "SENSOR-001", "FLOOD", "CRITICAL", "Flood emergency", "hcm", 1L);

    private static final String TOPIC = "com.uip.operator";

    @BeforeEach
    void setUp() {
        apnsAdapter = new ApnsAdapter(subscriptionRepository, apnsClient, TOPIC);
    }

    @Test
    void send_withNoApnsTokens_doesNotThrow() {
        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(Collections.emptyList());

        apnsAdapter.send(testNotification);

        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
        verifyNoMoreInteractions(subscriptionRepository);
    }

    @Test
    void send_withApnsTokens_processesAllTokens() throws Exception {
        PushSubscription sub1 = createApnsSubscription("apns-token-1");
        PushSubscription sub2 = createApnsSubscription("apns-token-2");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub1, sub2));
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class)))
                .thenReturn(mockFuture);
        when(mockFuture.get()).thenReturn(mockResponse);
        when(mockResponse.isAccepted()).thenReturn(true);

        apnsAdapter.send(testNotification);

        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
        verify(apnsClient, times(2)).sendNotification(any(SimpleApnsPushNotification.class));
    }

    @Test
    void send_filtersNonApnsPlatforms() throws Exception {
        PushSubscription fcmSub = PushSubscription.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).tenantId("hcm")
                .platform("fcm").endpoint("fcm://x").deviceToken("fcm-tok").active(true)
                .build();
        PushSubscription apnsSub = createApnsSubscription("apns-token-1");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(fcmSub, apnsSub));
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class)))
                .thenReturn(mockFuture);
        when(mockFuture.get()).thenReturn(mockResponse);
        when(mockResponse.isAccepted()).thenReturn(true);

        apnsAdapter.send(testNotification);

        // Only APNs token should be sent, FCM platform filtered out
        verify(apnsClient, times(1)).sendNotification(any(SimpleApnsPushNotification.class));
    }

    @Test
    void send_skipsSubscriptionWithoutDeviceToken() {
        PushSubscription noToken = PushSubscription.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).tenantId("hcm")
                .platform("apns").endpoint("endpoint").deviceToken(null).active(true)
                .build();

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(noToken));

        apnsAdapter.send(testNotification);

        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
        verifyNoMoreInteractions(subscriptionRepository);
    }

    @Test
    void send_apnsAcceptedResponse_doesNotThrow() throws Exception {
        PushSubscription sub = createApnsSubscription("valid-token");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class)))
                .thenReturn(mockFuture);
        when(mockFuture.get()).thenReturn(mockResponse);
        when(mockResponse.isAccepted()).thenReturn(true);

        assertDoesNotThrow(() -> apnsAdapter.send(testNotification));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void send_apnsRejectedResponse_doesNotPropagate() throws Exception {
        PushSubscription sub = createApnsSubscription("bad-token");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class)))
                .thenReturn(mockFuture);
        when(mockFuture.get()).thenReturn(mockResponse);
        when(mockResponse.isAccepted()).thenReturn(false);
        when(mockResponse.getRejectionReason()).thenReturn(Optional.of("Unregistered"));

        assertDoesNotThrow(() -> apnsAdapter.send(testNotification));

        // Unregistered token should be cleaned up
        verify(subscriptionRepository).save(sub);
        assertFalse(sub.getActive());
    }

    @Test
    void send_apnsRejectedNotInvalidToken_doesNotCleanup() throws Exception {
        PushSubscription sub = createApnsSubscription("valid-token");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class)))
                .thenReturn(mockFuture);
        when(mockFuture.get()).thenReturn(mockResponse);
        when(mockResponse.isAccepted()).thenReturn(false);
        when(mockResponse.getRejectionReason()).thenReturn(Optional.of("TopicDisallowed"));

        assertDoesNotThrow(() -> apnsAdapter.send(testNotification));

        verify(subscriptionRepository, never()).save(any());
        assertTrue(sub.getActive());
    }

    @Test
    void send_futureExecutionException_doesNotPropagate() throws Exception {
        PushSubscription sub = createApnsSubscription("fail-token");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class)))
                .thenReturn(mockFuture);
        when(mockFuture.get()).thenThrow(new ExecutionException("BadDeviceToken", new RuntimeException()));

        assertDoesNotThrow(() -> apnsAdapter.send(testNotification));

        // BadDeviceToken should trigger cleanup
        verify(subscriptionRepository).save(sub);
        assertFalse(sub.getActive());
    }

    @Test
    void send_noCredentials_gracefulNoOp() {
        ApnsAdapter noOpAdapter = new ApnsAdapter(subscriptionRepository, null, TOPIC);

        PushSubscription sub = createApnsSubscription("token-x");
        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));

        assertDoesNotThrow(() -> noOpAdapter.send(testNotification));
    }

    @Test
    void send_pushNotificationContainsTopicAndPayload() throws Exception {
        PushSubscription sub = createApnsSubscription("token-abc");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub));
        when(apnsClient.sendNotification(notificationCaptor.capture()))
                .thenReturn(mockFuture);
        when(mockFuture.get()).thenReturn(mockResponse);
        when(mockResponse.isAccepted()).thenReturn(true);

        apnsAdapter.send(testNotification);

        SimpleApnsPushNotification sentNotification = notificationCaptor.getValue();
        assertEquals(TOPIC, sentNotification.getTopic());
        String payload = sentNotification.getPayload();
        assertTrue(payload.contains("\"title\":\"Alert: CRITICAL\""));
        assertTrue(payload.contains("\"body\":\"Flood emergency\""));
        assertTrue(payload.contains("\"module\":\"FLOOD\""));
        assertTrue(payload.contains("\"sensorId\":\"SENSOR-001\""));
    }

    @Test
    void getChannelName_returnsApns() {
        assertEquals("apns", apnsAdapter.getChannelName());
    }

    private PushSubscription createApnsSubscription(String token) {
        return PushSubscription.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tenantId("hcm")
                .platform("apns")
                .endpoint("apns://" + token)
                .deviceToken(token)
                .active(true)
                .build();
    }
}
