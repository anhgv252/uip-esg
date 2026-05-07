package com.uip.backend.notification.service;

/**
 * Immutable notification payload dispatched through all registered channels.
 *
 * @param sensorId  originating sensor or device ID
 * @param module    source module (e.g. "environment", "traffic", "esg")
 * @param severity  alert severity level (INFO, WARNING, HIGH, CRITICAL)
 * @param message   human-readable alert description
 * @param tenantId  tenant scope for multi-tenant filtering
 * @param alertId   database ID of the alert event (nullable for non-persisted alerts)
 */
public record AlertNotification(
        String sensorId,
        String module,
        String severity,
        String message,
        String tenantId,
        Long alertId
) {
}
