package com.uip.backend.notification.service;

import com.uip.backend.notification.api.dto.PushSubscribeRequest;
import com.uip.backend.notification.api.dto.PushSubscriptionResponse;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import com.uip.backend.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("PushSubscriptionService — unit tests")
class PushSubscriptionServiceTest {

    @Mock
    private PushSubscriptionRepository repository;

    @InjectMocks
    private PushSubscriptionService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TENANT = "test-tenant";
    private static final String ENDPOINT = "https://push.example.com/sub/abc";

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PushSubscribeRequest buildRequest() {
        return new PushSubscribeRequest("web", ENDPOINT, "key123", "auth456", null, "Mozilla/5.0");
    }

    private PushSubscription buildSubscription(UUID id) {
        return PushSubscription.builder()
                .id(id)
                .userId(USER_ID)
                .tenantId(TENANT)
                .platform("web")
                .endpoint(ENDPOINT)
                .active(true)
                .build();
    }

    @Nested
    @DisplayName("subscribe()")
    class Subscribe {

        @Test
        @DisplayName("throws when max subscriptions reached")
        void throwsWhenMaxReached() {
            when(repository.countByUserIdAndActiveTrue(USER_ID)).thenReturn(10L);

            assertThatThrownBy(() -> service.subscribe(USER_ID, buildRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Maximum push subscriptions reached");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("creates new subscription when endpoint not found")
        void createsNewSubscription() {
            when(repository.countByUserIdAndActiveTrue(USER_ID)).thenReturn(0L);
            when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());

            UUID newId = UUID.randomUUID();
            PushSubscription saved = buildSubscription(newId);
            when(repository.save(any(PushSubscription.class))).thenReturn(saved);

            PushSubscriptionResponse response = service.subscribe(USER_ID, buildRequest());

            assertThat(response.id()).isEqualTo(newId);
            assertThat(response.platform()).isEqualTo("web");
            assertThat(response.active()).isTrue();
            verify(repository).save(any(PushSubscription.class));
        }

        @Test
        @DisplayName("updates existing subscription when endpoint already registered")
        void updatesExistingSubscription() {
            UUID existingId = UUID.randomUUID();
            PushSubscription existing = buildSubscription(existingId);
            existing.setP256dh("oldKey");

            when(repository.countByUserIdAndActiveTrue(USER_ID)).thenReturn(2L);
            when(repository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            PushSubscriptionResponse response = service.subscribe(USER_ID, buildRequest());

            assertThat(response.id()).isEqualTo(existingId);
            assertThat(existing.getP256dh()).isEqualTo("key123");
            verify(repository).save(existing);
        }
    }

    @Nested
    @DisplayName("unsubscribe()")
    class Unsubscribe {

        @Test
        @DisplayName("throws when subscription not found")
        void throwsWhenNotFound() {
            UUID subId = UUID.randomUUID();
            when(repository.findById(subId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.unsubscribe(subId, USER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Subscription not found");
        }

        @Test
        @DisplayName("throws SecurityException when user does not own subscription")
        void throwsWhenWrongUser() {
            UUID subId = UUID.randomUUID();
            UUID otherUser = UUID.randomUUID();
            PushSubscription sub = buildSubscription(subId);
            // sub.getUserId() == USER_ID, but caller is otherUser
            when(repository.findById(subId)).thenReturn(Optional.of(sub));

            assertThatThrownBy(() -> service.unsubscribe(subId, otherUser))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("cannot unsubscribe subscription owned by");
        }

        @Test
        @DisplayName("deactivates subscription successfully")
        void deactivatesSuccessfully() {
            UUID subId = UUID.randomUUID();
            PushSubscription sub = buildSubscription(subId);
            when(repository.findById(subId)).thenReturn(Optional.of(sub));
            when(repository.save(sub)).thenReturn(sub);

            service.unsubscribe(subId, USER_ID);

            assertThat(sub.getActive()).isFalse();
            verify(repository).save(sub);
        }
    }

    @Nested
    @DisplayName("listSubscriptions()")
    class ListSubscriptions {

        @Test
        @DisplayName("returns mapped responses for active subscriptions")
        void returnsMappedResponses() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            PushSubscription s1 = buildSubscription(id1);
            PushSubscription s2 = buildSubscription(id2);
            s2.setPlatform("android");

            when(repository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(s1, s2));

            List<PushSubscriptionResponse> result = service.listSubscriptions(USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(id1);
            assertThat(result.get(1).platform()).isEqualTo("android");
        }

        @Test
        @DisplayName("returns empty list when user has no active subscriptions")
        void returnsEmptyList() {
            when(repository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of());

            assertThat(service.listSubscriptions(USER_ID)).isEmpty();
        }
    }
}
