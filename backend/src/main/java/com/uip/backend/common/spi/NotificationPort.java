package com.uip.backend.common.spi;

/**
 * Port for routing alert notifications to delivery channels (FCM/APNs/Email/SSE)
 * without depending on {@code notification.service} directly (ADR-052, migration C2).
 *
 * <p>The {@code notification} module provides the implementation backed by its
 * {@code NotificationRouter}. Consumers ({@code safety}, {@code alert}) call this with
 * neutral primitives instead of constructing a {@code notification.domain.AlertNotification}
 * themselves.</p>
 */
public interface NotificationPort {

    /**
     * Route an alert notification to all relevant delivery channels for the tenant.
     *
     * @param sensorId  source sensor
     * @param category  notification category (e.g. {@code "STRUCTURAL"})
     * @param severity  alert severity (e.g. {@code "CRITICAL"})
     * @param message   human-readable message
     * @param tenantId  tenant owning the alert (RLS context)
     */
    void routeAlert(String sensorId, String category, String severity, String message, String tenantId);
}
