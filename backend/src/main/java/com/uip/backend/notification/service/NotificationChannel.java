package com.uip.backend.notification.service;

/**
 * Strategy interface for notification delivery channels.
 * Implementations: SSE, Web Push, (future) Email, SMS, FCM, APNs.
 */
public interface NotificationChannel {

    /**
     * Send an alert notification through this channel.
     * Implementations must handle exceptions internally and never propagate
     * to the caller (NotificationRouter handles per-channel isolation).
     */
    void send(AlertNotification notification);

    /**
     * Return the channel identifier used in logging and routing.
     */
    String getChannelName();
}
