package com.uip.backend.ai;

import com.uip.backend.ai.budget.TokenBudgetService;
import com.uip.backend.ai.cache.AiCacheMetrics;
import com.uip.backend.ai.flink.DistrictAggregationEvent;
import com.uip.backend.ai.routing.ModelRouter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M4-AI-01: Unit tests for the batched entry points added to
 * {@link AiInferenceService}: {@code analyzeBatch} (routing) and
 * {@code analyzeGenericConditions} (non-AQI path).
 *
 * <p>Mirrors {@code AiCacheConfigTest}'s self-proxy injection pattern — the
 * {@code @Cacheable} annotation does not fire under a plain unit test, but the
 * cache pre-check (hit/miss detection) still exercises the metric path.</p>
 */
@DisplayName("AiInferenceService — M4-AI-01 batch + generic path")
class AiInferenceServiceBatchTest {

    private CacheManager cacheManager;
    private AiCacheMetrics metrics;
    private TokenBudgetService budget;
    private AiInferenceService svc;

    @BeforeEach
    void setUp() throws Exception {
        cacheManager = new ConcurrentMapCacheManager("ai-responses");
        metrics = new AiCacheMetrics(new SimpleMeterRegistry());
        budget = mock(TokenBudgetService.class);
        when(budget.isWithinBudget(anyLong())).thenReturn(true);

        svc = new AiInferenceService(cacheManager, metrics,
                new AiCostMetrics(new SimpleMeterRegistry()), new ModelRouter(), budget);
        // Self-proxy so analyzeGenericConditions is invoked through the same instance.
        injectSelfProxy(svc, svc);
    }

    @Test
    @DisplayName("bucket(): value boundaries")
    void bucket_boundaries() {
        assertThat(AiInferenceService.bucket(0)).isEqualTo("lt10");
        assertThat(AiInferenceService.bucket(9.9)).isEqualTo("lt10");
        assertThat(AiInferenceService.bucket(10)).isEqualTo("10-50");
        assertThat(AiInferenceService.bucket(99.9)).isEqualTo("50-100");
        assertThat(AiInferenceService.bucket(100)).isEqualTo("100-500");
        assertThat(AiInferenceService.bucket(999)).isEqualTo("500-1000");
        assertThat(AiInferenceService.bucket(1000)).isEqualTo("gte1000");
    }

    @Test
    @DisplayName("analyzeBatch: AQI event → fallback when API key absent (no NPE)")
    void analyzeBatch_aqiEvent_returnsFallbackWithoutKey() {
        DistrictAggregationEvent event = event("HCM-D1", "AQI", 120.0);
        AiAnalysisResponse r = svc.analyzeBatch(event);

        assertThat(r).isNotNull();
        assertThat(r.districtCode()).isEqualTo("HCM-D1");
        // No API key configured → fallback model "none"
        assertThat(r.modelUsed()).isEqualTo("none");
    }

    @Test
    @DisplayName("analyzeBatch: non-AQI event (NOISE) → generic path, fallback model")
    void analyzeBatch_noiseEvent_returnsGenericFallback() {
        DistrictAggregationEvent event = event("HCM-D2", "NOISE", 85.0);
        AiAnalysisResponse r = svc.analyzeBatch(event);

        assertThat(r).isNotNull();
        assertThat(r.districtCode()).isEqualTo("HCM-D2");
        assertThat(r.modelUsed()).isEqualTo("none");
    }

    @Test
    @DisplayName("analyzeBatch: null event → fallback, no exception")
    void analyzeBatch_nullEvent_returnsFallback() {
        AiAnalysisResponse r = svc.analyzeBatch(null);
        assertThat(r).isNotNull();
    }

    @Test
    @DisplayName("analyzeBatch: event missing district → fallback")
    void analyzeBatch_missingDistrict_returnsFallback() {
        DistrictAggregationEvent event = new DistrictAggregationEvent(
                "t", null, "AQI", 1, 50.0, 50.0, 1L, 2L, List.of());
        AiAnalysisResponse r = svc.analyzeBatch(event);
        assertThat(r).isNotNull();
    }

    @Test
    @DisplayName("analyzeBatch: AQI event increments miss counter (cold cache)")
    void analyzeBatch_aqiEvent_incrementsMiss() {
        svc.analyzeBatch(event("HCM-D1", "AQI", 120.0));
        assertThat(metrics.missCount()).isEqualTo(1.0);
        assertThat(metrics.hitCount()).isZero();
    }

    @Test
    @DisplayName("analyzeBatch: budget exceeded → fallback, no API call")
    void analyzeBatch_budgetExceeded_returnsFallback() {
        when(budget.isWithinBudget(anyLong())).thenReturn(false);
        AiAnalysisResponse r = svc.analyzeBatch(event("HCM-D1", "NOISE", 85.0));
        assertThat(r.modelUsed()).isEqualTo("none");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static DistrictAggregationEvent event(String district, String type, double max) {
        return new DistrictAggregationEvent("tenant-A", district, type, 3, max, max - 10,
                1L, 2L, List.of(new DistrictAggregationEvent.SensorSnapshot("s1", max, 1L)));
    }

    private static void injectSelfProxy(AiInferenceService target, AiInferenceService proxy) throws Exception {
        Field f = AiInferenceService.class.getDeclaredField("self");
        f.setAccessible(true);
        f.set(target, proxy);
    }
}
