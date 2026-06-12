package com.uip.backend.ai.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * M4-AI-04: Micrometer counters for AI response cache hit/miss monitoring.
 *
 * <p>Exposes two Prometheus counters:
 * <ul>
 *   <li>{@code ai_cache_hits_total} — incremented each time a cached response is
 *       returned without calling the Claude API.</li>
 *   <li>{@code ai_cache_misses_total} — incremented each time the cache is cold and
 *       the Claude API must be called.</li>
 * </ul>
 * </p>
 *
 * <p>Hit rate panel query (Grafana):
 * <pre>
 *   ai_cache_hits_total / (ai_cache_hits_total + ai_cache_misses_total)
 * </pre>
 * </p>
 *
 * <p>These counters are separate from Spring Boot's built-in
 * {@code cache_gets_total{name="ai-responses", result="hit|miss"}} metrics
 * (enabled via {@code management.metrics.cache.instrument-enabled=true}) to allow
 * targeted dashboard panels with stable metric names independent of the cache
 * provider.</p>
 */
@Component
public class AiCacheMetrics {

    private final Counter hitCounter;
    private final Counter missCounter;

    public AiCacheMetrics(MeterRegistry registry) {
        hitCounter = Counter.builder("ai_cache_hits_total")
                .description("Number of AI inference requests served from Redis cache (DB 2)")
                .tag("cache", "ai-responses")
                .register(registry);

        missCounter = Counter.builder("ai_cache_misses_total")
                .description("Number of AI inference requests that required a live Claude API call")
                .tag("cache", "ai-responses")
                .register(registry);
    }

    /** Call when a cached AI response is returned without contacting Claude API. */
    public void recordHit() {
        hitCounter.increment();
    }

    /** Call when no cached response exists and the Claude API is invoked. */
    public void recordMiss() {
        missCounter.increment();
    }

    /** Cumulative hit count — exposed for unit testing. */
    public double hitCount() {
        return hitCounter.count();
    }

    /** Cumulative miss count — exposed for unit testing. */
    public double missCount() {
        return missCounter.count();
    }
}
