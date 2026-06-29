package com.uip.backend.ai.nl.domain;

/**
 * Immutable request for NL→BPMN intent parsing.
 * 
 * <p>ADR-049 §4.2: Contains raw operator text (may include PII).
 */
public record NLParseRequest(
    String text,              // Raw Vietnamese operator input
    String workflowContext,   // Optional: e.g. "flood_response", "energy_mgmt"
    boolean gdprMode,         // From X-GDPR-Mode header; default true for operator UI
    String tenantId,          // From JWT claim, injected by controller
    String requestId          // For audit log correlation
) {
    public NLParseRequest {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }
}
