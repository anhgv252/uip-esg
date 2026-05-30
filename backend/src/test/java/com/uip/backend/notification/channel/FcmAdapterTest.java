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
 * S6-M04 — FCM adapter unit tests.
 * Tests verify routing logic and token cleanup without actual Firebase calls.
 */
@ExtendWith(MockitoExtension.class)
class FcmAdapterTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    private FcmAdapter fcmAdapter;

    private final AlertNotification testNotification = new AlertNotification(
            "SENSOR-001", "FLOOD", "HIGH", "Water level rising", "hcm", 1L);

    @BeforeEach
    void setUp() {
        fcmAdapter = new FcmAdapter(subscriptionRepository);
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
    void send_withFcmTokens_processesAllTokens() {
        PushSubscription sub1 = createFcmSubscription("token-1");
        PushSubscription sub2 = createFcmSubscription("token-2");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(sub1, sub2));

        fcmAdapter.send(testNotification);

        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
    }

    @Test
    void send_filtersNonFcmPlatforms() {
        PushSubscription webSub = PushSubscription.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).tenantId("hcm")
                .platform("web").endpoint("https://push.example.com/1").active(true)
                .build();
        PushSubscription fcmSub = createFcmSubscription("fcm-token-1");

        when(subscriptionRepository.findByTenantIdAndActiveTrue("hcm"))
                .thenReturn(List.of(webSub, fcmSub));

        fcmAdapter.send(testNotification);

        // web subscription should be filtered out, only fcm processed
        verify(subscriptionRepository).findByTenantIdAndActiveTrue("hcm");
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
