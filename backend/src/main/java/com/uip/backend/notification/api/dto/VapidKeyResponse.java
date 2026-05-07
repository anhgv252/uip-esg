package com.uip.backend.notification.api.dto;

/**
 * Public VAPID key response for client-side push subscription setup.
 */
public record VapidKeyResponse(String publicKey) {
}
