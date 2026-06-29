package com.uip.backend.ai.nl.domain;

/**
 * Reason for a ModelRouter routing decision (for audit logs).
 * 
 * <p>ADR-049 §4.2: Log routing decisions without PII content.
 */
public enum RoutingReason {
    /**
     * X-GDPR-Mode: true header was set; PII scan skipped.
     */
    GDPR_MODE_HEADER,
    
    /**
     * PII detected in text by VietnamesePiiPatterns scan.
     */
    PII_DETECTED,
    
    /**
     * No PII found; gdpr_mode=false; routed to cloud for quality.
     */
    NON_PII
}
