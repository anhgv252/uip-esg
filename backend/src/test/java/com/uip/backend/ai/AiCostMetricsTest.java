package com.uip.backend.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * M4-AI-06: Unit tests for AiCostMetrics using SimpleMeterRegistry (no Spring context).
 */
class AiCostMetricsTest {

    private MeterRegistry registry;
    private AiCostMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new AiCostMetrics(registry);
    }

    // ── recordCall — token counters ──────────────────────────────────────────────

    @Test
    @DisplayName("recordCall increments ai_tokens_input_total by inputTokens")
    void recordCall_incrementsInputTokenCounter() {
        metrics.recordCall("haiku", "tenant-a", 1000, 200);

        double count = registry.counter("ai_tokens_input_total",
                "model", "haiku", "tenant_id", "tenant-a").count();
        assertThat(count).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("recordCall increments ai_tokens_output_total by outputTokens")
    void recordCall_incrementsOutputTokenCounter() {
        metrics.recordCall("haiku", "tenant-b", 500, 150);

        double count = registry.counter("ai_tokens_output_total",
                "model", "haiku", "tenant_id", "tenant-b").count();
        assertThat(count).isEqualTo(150.0);
    }

    // ── recordCall — cost calculation (Haiku) ────────────────────────────────────

    @Test
    @DisplayName("recordCall: Haiku cost = input*0.25/1M + output*1.25/1M")
    void recordCall_haikuCostCalculation() {
        // 1_000_000 input tokens → $0.25; 0 output → $0.00; total = $0.25
        metrics.recordCall("claude-haiku-20240307", "t1", 1_000_000, 0);

        double cost = registry.counter("ai_cost_usd_total",
                "model", "haiku", "tenant_id", "t1").count();
        assertThat(cost).isCloseTo(0.25, within(1e-9));
    }

    @Test
    @DisplayName("recordCall: Haiku output cost = 1.25 per 1M output tokens")
    void recordCall_haikuOutputCost() {
        metrics.recordCall("haiku", "t2", 0, 1_000_000);

        double cost = registry.counter("ai_cost_usd_total",
                "model", "haiku", "tenant_id", "t2").count();
        assertThat(cost).isCloseTo(1.25, within(1e-9));
    }

    // ── recordCall — cost calculation (Sonnet) ────────────────────────────────────

    @Test
    @DisplayName("recordCall: Sonnet input cost = 3.00 per 1M input tokens")
    void recordCall_sonnetInputCost() {
        metrics.recordCall("claude-sonnet-20240229", "t3", 1_000_000, 0);

        double cost = registry.counter("ai_cost_usd_total",
                "model", "sonnet", "tenant_id", "t3").count();
        assertThat(cost).isCloseTo(3.00, within(1e-9));
    }

    @Test
    @DisplayName("recordCall: Sonnet output cost = 15.00 per 1M output tokens")
    void recordCall_sonnetOutputCost() {
        metrics.recordCall("sonnet", "t4", 0, 1_000_000);

        double cost = registry.counter("ai_cost_usd_total",
                "model", "sonnet", "tenant_id", "t4").count();
        assertThat(cost).isCloseTo(15.00, within(1e-9));
    }

    // ── recordCall — request success counter ──────────────────────────────────────

    @Test
    @DisplayName("recordCall increments ai_requests_total with status=success")
    void recordCall_incrementsSuccessRequestCounter() {
        metrics.recordCall("haiku", "tenant-x", 100, 50);
        metrics.recordCall("haiku", "tenant-x", 200, 80);

        double count = registry.counter("ai_requests_total",
                "model", "haiku", "tenant_id", "tenant-x", "status", "success").count();
        assertThat(count).isEqualTo(2.0);
    }

    // ── recordCacheHit ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordCacheHit increments ai_requests_total with status=cache_hit")
    void recordCacheHit_incrementsCacheHitCounter() {
        metrics.recordCacheHit("tenant-y");
        metrics.recordCacheHit("tenant-y");

        double count = registry.counter("ai_requests_total",
                "model", "cache", "tenant_id", "tenant-y", "status", "cache_hit").count();
        assertThat(count).isEqualTo(2.0);
    }

    // ── tenant_id tag isolation ───────────────────────────────────────────────────

    @Test
    @DisplayName("tenant_id tag is present on ai_cost_usd_total counter")
    void recordCall_tenantIdTagIsPresent() {
        metrics.recordCall("haiku", "tenant-alpha", 100_000, 50_000);

        Counter counter = registry.find("ai_cost_usd_total")
                .tag("tenant_id", "tenant-alpha")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isPositive();
    }

    // ── null tenantId fallback ────────────────────────────────────────────────────

    @Test
    @DisplayName("null tenantId falls back to 'default' tag value")
    void recordCall_nullTenantIdFallsBackToDefault() {
        metrics.recordCall("haiku", null, 10, 5);

        Counter counter = registry.find("ai_tokens_input_total")
                .tag("tenant_id", "default")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(10.0);
    }
}
