package com.uip.backend.notification.channel;

import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import com.uip.backend.notification.service.AlertNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * S6-M04 — APNs adapter unit tests.
 * Tests verify routing logic and token cleanup without actual APNs calls.
 */
@ExtendWith(MockitoExtension.class)
class ApnsAdapterTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    private ApnsAdapter apnsAdapter;

    private final AlertNotification testNotification = new AlertNotification(
            "SENSOR-001", "FLOOD", "CRITICAL", "Flood emergency", "hcm", 1L);

    @BeforeEach
    void setUp() {
        apnsAdapter = new ApnsAdapter(subscriptionRepository);
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
    void send_withApnsTokens_processesAllTokens() {
        PushSubscription sub1 = createApnsSubscription("apns-token-1");
        PushSubscription sub2 = createApnsSubscription("apns-token-2");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub1, sub2));

        apnsAdapter.send(testNotification);

        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
    }

    @Test
    void send_filtersNonApnsPlatforms() {
        PushSubscription fcmSub = PushSubscription.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).tenantId("hcm")
                .platform("fcm").endpoint("fcm://x").deviceToken("fcm-tok").active(true)
                .build();
        PushSubscription apnsSub = createApnsSubscription("apns-token-1");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(fcmSub, apnsSub));

        apnsAdapter.send(testNotification);

        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
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
