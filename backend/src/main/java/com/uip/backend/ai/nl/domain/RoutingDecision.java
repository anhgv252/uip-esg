package com.uip.backend.ai.nl.domain;

/**
 * ModelRouter routing decision result.
 * 
 * <p>ADR-049 §4.2: Immutable decision with audit metadata.
 */
public record RoutingDecision(
    Route route,              // LOCAL or CLOUD
    RoutingReason reason,     // Why this route was chosen
    long piiScanMs            // PII scan latency; 0 if gdpr_mode skipped scan
) {
    public RoutingDecision {
        if (route == null) {
            throw new IllegalArgumentException("route must not be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        if (piiScanMs < 0) {
            throw new IllegalArgumentException("piiScanMs must be >= 0");
        }
    }
}
