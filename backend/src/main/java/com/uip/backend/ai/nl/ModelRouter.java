package com.uip.backend.ai.nl;

import com.uip.backend.ai.nl.domain.NLParseRequest;
import com.uip.backend.ai.nl.domain.RoutingDecision;

/**
 * Routes NL→BPMN inference requests based on gdpr_mode flag
 * and content-based PII detection.
 * 
 * <p>ADR-049 §4.2: Hybrid routing for Decree 13/2023 compliance.
 * 
 * <p>Routing invariants:
 * <ul>
 *   <li>gdpr_mode=true → always route to LOCAL (on-prem ViT5)</li>
 *   <li>gdpr_mode=false → run PII scan; if PII detected, escalate to LOCAL</li>
 *   <li>Non-PII → route to CLOUD (Claude API) for quality</li>
 *   <li>On-prem failure + PII → fail-closed with 503 (never fall back to cloud)</li>
 * </ul>
 * 
 * <p>Implementors MUST be thread-safe.
 */
public interface ModelRouter {

    /**
     * Route an NL parse request to LOCAL or CLOUD model.
     * 
     * @param request  the incoming NL parse request (immutable DTO)
     * @return routing decision — never null
     * @throws ModelRouterException if routing itself fails (config error, etc.)
     */
    RoutingDecision route(NLParseRequest request);
}
