package com.uip.backend.notification.service;

import com.uip.backend.notification.api.dto.PushSubscribeRequest;
import com.uip.backend.notification.api.dto.PushSubscriptionResponse;
import com.uip.backend.notification.domain.PushSubscription;
import com.uip.backend.notification.repository.PushSubscriptionRepository;
import com.uip.backend.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages push notification subscription lifecycle:
 * subscribe, unsubscribe, list.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushSubscriptionService {

    private static final int MAX_SUBSCRIPTIONS_PER_USER = 10;

    private final PushSubscriptionRepository repository;

    /**
     * Register or update a push subscription for the given user.
     * If the endpoint already exists, the existing record is updated (upsert).
     * Enforces a maximum of {@value MAX_SUBSCRIPTIONS_PER_USER} active subscriptions per user.
     */
    @Transactional
    public PushSubscriptionResponse subscribe(UUID userId, PushSubscribeRequest request) {
        long activeCount = repository.countByUserIdAndActiveTrue(userId);
        if (activeCount >= MAX_SUBSCRIPTIONS_PER_USER) {
            throw new IllegalStateException(
                    "Maximum push subscriptions reached (%d) for user %s"
                            .formatted(MAX_SUBSCRIPTIONS_PER_USER, userId));
        }

        String tenantId = TenantContext.getCurrentTenant();

        PushSubscription sub = repository.findByEndpoint(request.endpoint())
                .orElseGet(() -> PushSubscription.builder()
                        .userId(userId)
                        .tenantId(tenantId)
                        .endpoint(request.endpoint())
                        .active(true)
                        .build());

        sub.setPlatform(request.platform());
        sub.setP256dh(request.p256dh());
        sub.setAuthKey(request.authKey());
        sub.setDeviceToken(request.deviceToken());
        sub.setUserAgent(request.userAgent());
        sub.setTenantId(tenantId);
        sub.setActive(true);

        PushSubscription saved = repository.save(sub);

        log.info("Push subscription upserted: id={} userId={} platform={} tenant={}",
                saved.getId(), userId, request.platform(), tenantId);

        return toResponse(saved);
    }

    /**
     * Deactivate a push subscription. Only the owning user can unsubscribe.
     */
    @Transactional
    public void unsubscribe(UUID subscriptionId, UUID userId) {
        PushSubscription sub = repository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Subscription not found: " + subscriptionId));

        if (!sub.getUserId().equals(userId)) {
            throw new SecurityException(
                    "User %s cannot unsubscribe subscription owned by %s"
                            .formatted(userId, sub.getUserId()));
        }

        sub.setActive(false);
        repository.save(sub);

        log.info("Push subscription deactivated: id={} userId={}", subscriptionId, userId);
    }

    /**
     * List all active push subscriptions for the given user.
     */
    @Transactional(readOnly = true)
    public List<PushSubscriptionResponse> listSubscriptions(UUID userId) {
        return repository.findByUserIdAndActiveTrue(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private PushSubscriptionResponse toResponse(PushSubscription sub) {
        return new PushSubscriptionResponse(
                sub.getId(),
                sub.getPlatform(),
                sub.getEndpoint(),
                sub.getActive(),
                sub.getCreatedAt()
        );
    }
}
