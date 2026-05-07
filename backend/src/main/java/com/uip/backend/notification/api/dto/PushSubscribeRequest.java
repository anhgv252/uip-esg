package com.uip.backend.notification.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for registering a push notification subscription.
 */
public record PushSubscribeRequest(
        @NotBlank String platform,
        @NotBlank String endpoint,
        String p256dh,
        String authKey,
        String deviceToken,
        String userAgent
) {
}
