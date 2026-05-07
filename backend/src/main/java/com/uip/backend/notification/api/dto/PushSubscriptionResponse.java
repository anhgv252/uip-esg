package com.uip.backend.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for push subscription operations.
 */
public record PushSubscriptionResponse(
        UUID id,
        String platform,
        String endpoint,
        boolean active,
        Instant createdAt
) {
}
