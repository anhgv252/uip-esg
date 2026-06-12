package com.uip.backend.ai.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * M4-AI-03: Smart pre-filter — routes sensor events to the appropriate handling path
 * (immediate action, rule-based, or AI inference) before dispatching to the inference pipeline.
 *
 * <p>Routing logic:
 * <ul>
 *   <li>KNOWN_CRITICAL_TYPES → BYPASS_AI (direct emergency action, zero latency)</li>
 *   <li>ratio &gt; 0.5 (clear threshold violation) → HANDLE_RULE_BASED (deterministic)</li>
 *   <li>uncertain / close to threshold → ESCALATE_AI (ML inference)</li>
 * </ul>
 * </p>
 */
@Component
@Slf4j
public class SmartPreFilter {

    /** Event types that bypass AI and go directly to emergency handling. */
    private static final Set<String> KNOWN_CRITICAL_TYPES =
            Set.of("FLOOD_ALERT", "FIRE_ALARM", "EVACUATION");

    public enum FilterDecision {
        /** Critical safety event — skip AI, trigger immediate action. */
        BYPASS_AI,
        /** Clearly abnormal reading — deterministic rule engine is sufficient. */
        HANDLE_RULE_BASED,
        /** Ambiguous / borderline reading — escalate to AI inference. */
        ESCALATE_AI
    }

    /**
     * Evaluate a sensor reading and decide how to route it.
     *
     * @param measureType sensor measure type key (e.g. "AQI", "FLOOD_ALERT")
     * @param value       current sensor reading
     * @param threshold   configured alert threshold for this measure type
     * @return routing decision
     */
    public FilterDecision evaluate(String measureType, double value, double threshold) {
        if (KNOWN_CRITICAL_TYPES.contains(measureType)) {
            log.warn("[SmartPreFilter] Critical bypass: measureType={} value={}", measureType, value);
            return FilterDecision.BYPASS_AI;
        }

        double divisor = threshold == 0 ? 1 : threshold;
        double ratio = Math.abs(value - threshold) / divisor;

        if (ratio > 0.5) {
            log.debug("[SmartPreFilter] Rule-based: measureType={} value={} threshold={} ratio={}",
                    measureType, value, threshold, ratio);
            return FilterDecision.HANDLE_RULE_BASED;
        }

        log.debug("[SmartPreFilter] Escalate to AI: measureType={} value={} threshold={} ratio={}",
                measureType, value, threshold, ratio);
        return FilterDecision.ESCALATE_AI;
    }

    /**
     * Returns {@code true} when the event type is in the critical bypass list
     * and must never be delayed by AI inference.
     */
    public boolean isCriticalBypass(String measureType) {
        return KNOWN_CRITICAL_TYPES.contains(measureType);
    }
}
