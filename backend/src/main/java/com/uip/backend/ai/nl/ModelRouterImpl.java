package com.uip.backend.ai.nl;

import com.uip.backend.ai.nl.domain.NLParseRequest;
import com.uip.backend.ai.nl.domain.Route;
import com.uip.backend.ai.nl.domain.RoutingDecision;
import com.uip.backend.ai.nl.domain.RoutingReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hybrid model routing implementation.
 * 
 * <p>ADR-049 §4.1 routing logic:
 * <ol>
 *   <li>If gdpr_mode=true → route=LOCAL (skip PII scan)</li>
 *   <li>Else: run PII scan on text</li>
 *   <li>If PII detected → route=LOCAL</li>
 *   <li>If no PII → route=CLOUD</li>
 * </ol>
 * 
 * <p>Thread-safe (stateless).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModelRouterImpl implements ModelRouter {

    private final VietnamesePiiPatterns piiPatterns;

    @Override
    public RoutingDecision route(NLParseRequest request) {
        // Invariant 1: gdpr_mode=true → always LOCAL (skip scan)
        if (request.gdprMode()) {
            log.debug("Routing to LOCAL (gdpr_mode=true), requestId={}", request.requestId());
            return new RoutingDecision(Route.LOCAL, RoutingReason.GDPR_MODE_HEADER, 0L);
        }
        
        // Invariant 2: run PII scan
        long scanStart = System.currentTimeMillis();
        boolean hasPii = piiPatterns.hasPii(request.text());
        long scanMs = System.currentTimeMillis() - scanStart;
        
        if (hasPii) {
            log.debug("Routing to LOCAL (PII detected), requestId={}, scanMs={}", 
                     request.requestId(), scanMs);
            return new RoutingDecision(Route.LOCAL, RoutingReason.PII_DETECTED, scanMs);
        }
        
        // Invariant 3: non-PII → CLOUD for quality
        log.debug("Routing to CLOUD (non-PII), requestId={}, scanMs={}", 
                 request.requestId(), scanMs);
        return new RoutingDecision(Route.CLOUD, RoutingReason.NON_PII, scanMs);
    }
}
