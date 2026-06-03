package com.uip.backend.safety.model;

import java.time.Instant;

/**
 * Building safety score snapshot — range 0-100.
 *
 * <p>Score formula (see {@link com.uip.backend.safety.service.BuildingSafetyService}):</p>
 * <pre>score = 100 − (CRITICAL_count × 30) − (WARNING_count × 10), clamped to [0, 100]</pre>
 *
 * <p><strong>BR-010:</strong> Score is informational. Operators review P0 alerts;
 * the system never auto-evacuates based on this score.</p>
 */
public record SafetyScore(
        String buildingId,
        int score,
        String status,       // SAFE | WARNING | CRITICAL | OFFLINE
        Instant lastUpdated,
        int activeAlerts
) {
    public static SafetyScore offline(String buildingId) {
        return new SafetyScore(buildingId, 0, "OFFLINE", Instant.now(), 0);
    }
}
