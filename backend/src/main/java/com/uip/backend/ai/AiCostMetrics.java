package com.uip.backend.ai;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * M4-AI-06: Micrometer counters for AI cost tracking and per-tenant chargeback.
 *
 * <p>Exposes four Prometheus metrics:
 * <ul>
 *   <li>{@code ai_tokens_input_total{model,tenant_id}} — input (prompt) tokens consumed</li>
 *   <li>{@code ai_tokens_output_total{model,tenant_id}} — output (completion) tokens produced</li>
 *   <li>{@code ai_cost_usd_total{model,tenant_id}} — estimated USD cost (counter)</li>
 *   <li>{@code ai_requests_total{model,tenant_id,status}} — request count by status
 *       (success | error | cache_hit)</li>
 * </ul>
 * </p>
 *
 * <p>Cost rates (USD per 1 million tokens):
 * <pre>
 *   Haiku:  $0.25 / 1M input,  $1.25 / 1M output
 *   Sonnet: $3.00 / 1M input, $15.00 / 1M output
 * </pre>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AiCostMetrics {

    // ── Cost rates (USD per million tokens) ─────────────────────────────────────
    static final double HAIKU_INPUT_COST_PER_M   = 0.25;
    static final double HAIKU_OUTPUT_COST_PER_M  = 1.25;
    static final double SONNET_INPUT_COST_PER_M  = 3.00;
    static final double SONNET_OUTPUT_COST_PER_M = 15.00;

    private final MeterRegistry registry;

    // ── Public API ───────────────────────────────────────────────────────────────

    /**
     * Records a completed Claude API call: token usage + estimated cost.
     *
     * @param model        model identifier (e.g. "claude-haiku-20240307")
     * @param tenantId     tenant identifier used for per-tenant chargeback
     * @param inputTokens  number of input (prompt) tokens consumed
     * @param outputTokens number of output (completion) tokens produced
     */
    public void recordCall(String model, String tenantId, int inputTokens, int outputTokens) {
        String m = normalizeModel(model);
        String t = tenantId != null ? tenantId : "default";

        registry.counter("ai_tokens_input_total",  "model", m, "tenant_id", t)
                .increment(inputTokens);
        registry.counter("ai_tokens_output_total", "model", m, "tenant_id", t)
                .increment(outputTokens);
        registry.counter("ai_cost_usd_total",      "model", m, "tenant_id", t)
                .increment(estimateCost(m, inputTokens, outputTokens));
        registry.counter("ai_requests_total",      "model", m, "tenant_id", t, "status", "success")
                .increment();
    }

    /**
     * Records a request served from the Redis AI-response cache.
     * No Claude API call was made, so no token or cost counters are updated.
     *
     * @param tenantId tenant identifier for chargeback
     */
    public void recordCacheHit(String tenantId) {
        String t = tenantId != null ? tenantId : "default";
        registry.counter("ai_requests_total", "model", "cache", "tenant_id", t, "status", "cache_hit")
                .increment();
    }

    /**
     * Records a failed Claude API call (network error, rate-limit, etc.).
     *
     * @param model    model identifier
     * @param tenantId tenant identifier for chargeback
     */
    public void recordError(String model, String tenantId) {
        String m = normalizeModel(model);
        String t = tenantId != null ? tenantId : "default";
        registry.counter("ai_requests_total", "model", m, "tenant_id", t, "status", "error")
                .increment();
    }

    // ── Package-private helpers (accessible from unit tests) ─────────────────────

    /**
     * Estimates USD cost for a single API call.
     *
     * @param model        normalised model name ("haiku" or "sonnet")
     * @param inputTokens  prompt token count
     * @param outputTokens completion token count
     * @return estimated cost in USD
     */
    double estimateCost(String model, int inputTokens, int outputTokens) {
        double inputRate  = model.contains("sonnet") ? SONNET_INPUT_COST_PER_M  : HAIKU_INPUT_COST_PER_M;
        double outputRate = model.contains("sonnet") ? SONNET_OUTPUT_COST_PER_M : HAIKU_OUTPUT_COST_PER_M;
        return (inputTokens  / 1_000_000.0 * inputRate)
             + (outputTokens / 1_000_000.0 * outputRate);
    }

    /**
     * Normalises raw model identifiers to canonical short names for tag cardinality control.
     *
     * @param model raw model string (e.g. "claude-haiku-20240307")
     * @return "haiku", "sonnet", or the lowercase original
     */
    String normalizeModel(String model) {
        if (model == null || model.isBlank()) return "unknown";
        String lower = model.toLowerCase();
        if (lower.contains("sonnet")) return "sonnet";
        if (lower.contains("haiku"))  return "haiku";
        return lower;
    }
}
