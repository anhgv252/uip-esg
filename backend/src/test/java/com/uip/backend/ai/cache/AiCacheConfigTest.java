package com.uip.backend.ai.cache;

import com.uip.backend.ai.AiAnalysisResponse;
import com.uip.backend.ai.AiCostMetrics;
import com.uip.backend.ai.AiInferenceService;
import com.uip.backend.ai.budget.TokenBudgetService;
import com.uip.backend.ai.routing.ModelRouter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for M4-AI-04 AI response cache configuration and metrics.
 *
 * <p>Tests verify:
 * <ol>
 *   <li>AQI bucketing correctness across all six EPA bands</li>
 *   <li>Cache TTL constant is 300 s (5 minutes)</li>
 *   <li>Cache name constant is "ai-responses"</li>
 *   <li>Fallback CacheManager is created when Redis is not present</li>
 *   <li>AiCacheMetrics counters are incremented correctly</li>
 *   <li>analyzeAqiWithMetrics distinguishes cache hit from cache miss</li>
 *   <li>Expected hit rate with warm cache exceeds 50% target</li>
 * </ol>
 * </p>
 */
@DisplayName("AiCacheConfig — unit")
class AiCacheConfigTest {

    // ─── AqiRangeBucket tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("AQI 0 → bucket '0-50'")
    void bucket_zero() {
        assertThat(AqiRangeBucket.bucket(0)).isEqualTo("0-50");
    }

    @Test
    @DisplayName("AQI 50 → bucket '0-50' (boundary)")
    void bucket_boundary_50() {
        assertThat(AqiRangeBucket.bucket(50)).isEqualTo("0-50");
    }

    @Test
    @DisplayName("AQI 51 → bucket '51-100'")
    void bucket_51() {
        assertThat(AqiRangeBucket.bucket(51)).isEqualTo("51-100");
    }

    @Test
    @DisplayName("AQI 100 → bucket '51-100' (boundary)")
    void bucket_boundary_100() {
        assertThat(AqiRangeBucket.bucket(100)).isEqualTo("51-100");
    }

    @Test
    @DisplayName("AQI 101 → bucket '101-150'")
    void bucket_101() {
        assertThat(AqiRangeBucket.bucket(101)).isEqualTo("101-150");
    }

    @Test
    @DisplayName("AQI 151 → bucket '151-200'")
    void bucket_151() {
        assertThat(AqiRangeBucket.bucket(151)).isEqualTo("151-200");
    }

    @Test
    @DisplayName("AQI 201 → bucket '201-300'")
    void bucket_201() {
        assertThat(AqiRangeBucket.bucket(201)).isEqualTo("201-300");
    }

    @Test
    @DisplayName("AQI 301 → bucket '301-500'")
    void bucket_301() {
        assertThat(AqiRangeBucket.bucket(301)).isEqualTo("301-500");
    }

    @Test
    @DisplayName("AQI 500 → bucket '301-500' (upper boundary)")
    void bucket_500() {
        assertThat(AqiRangeBucket.bucket(500)).isEqualTo("301-500");
    }

    @Test
    @DisplayName("Negative AQI → bucket '0-50' (treated as below 0)")
    void bucket_negative() {
        assertThat(AqiRangeBucket.bucket(-10)).isEqualTo("0-50");
    }

    // ─── Cache config constants ───────────────────────────────────────────────

    @Test
    @DisplayName("AI_RESPONSE_TTL is 300 seconds (5 minutes)")
    void ttlIs300Seconds() {
        assertThat(AiCacheConfig.AI_RESPONSE_TTL).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("CACHE_NAME is 'ai-responses'")
    void cacheNameIsAiResponses() {
        assertThat(AiCacheConfig.CACHE_NAME).isEqualTo("ai-responses");
    }

    // ─── Fallback CacheManager ────────────────────────────────────────────────

    @Test
    @DisplayName("aiResponseFallbackCacheManager creates ConcurrentMapCacheManager for 'ai-responses'")
    void fallbackCacheManager_registersAiResponsesCache() {
        AiCacheConfig config = new AiCacheConfig();
        CacheManager cm = config.aiResponseFallbackCacheManager();

        assertThat(cm).isInstanceOf(ConcurrentMapCacheManager.class);
        Cache cache = cm.getCache("ai-responses");
        assertThat(cache).isNotNull();
    }

    @Test
    @DisplayName("Fallback cache supports put/get cycle")
    void fallbackCache_putGet_works() {
        AiCacheConfig config = new AiCacheConfig();
        CacheManager cm = config.aiResponseFallbackCacheManager();
        Cache cache = cm.getCache("ai-responses");
        assertThat(cache).isNotNull();

        AiAnalysisResponse resp = new AiAnalysisResponse("HCM-D1", "51-100", "Moderate air quality.", "haiku", 0L);
        cache.put("HCM-D1:51-100", resp);

        Cache.ValueWrapper found = cache.get("HCM-D1:51-100");
        assertThat(found).isNotNull();
        assertThat(found.get()).isEqualTo(resp);
    }

    // ─── AiCacheMetrics ───────────────────────────────────────────────────────

    private AiCacheMetrics metrics;

    @BeforeEach
    void setUpMetrics() {
        metrics = new AiCacheMetrics(new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("recordHit increments hitCount")
    void recordHit_incrementsCounter() {
        metrics.recordHit();
        metrics.recordHit();
        assertThat(metrics.hitCount()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("recordMiss increments missCount")
    void recordMiss_incrementsCounter() {
        metrics.recordMiss();
        assertThat(metrics.missCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("hit and miss counters are independent")
    void hitAndMissCountersAreIndependent() {
        metrics.recordHit();
        metrics.recordHit();
        metrics.recordMiss();
        assertThat(metrics.hitCount()).isEqualTo(2.0);
        assertThat(metrics.missCount()).isEqualTo(1.0);
    }

    // ─── analyzeAqiWithMetrics — hit/miss detection ───────────────────────────

    @Test
    @DisplayName("Cache MISS: miss counter incremented when cache is empty before call")
    void analyzeAqiWithMetrics_cacheMiss_incrementsMissCounter() {
        CacheManager cacheMgr = new ConcurrentMapCacheManager("ai-responses");
        AiCacheMetrics localMetrics = new AiCacheMetrics(new SimpleMeterRegistry());
        ModelRouter router = new ModelRouter();
        TokenBudgetService budget = mock(TokenBudgetService.class);
        when(budget.isWithinBudget(anyLong())).thenReturn(true);

        AiCostMetrics costMetrics = new AiCostMetrics(new SimpleMeterRegistry());
        AiInferenceService svc = new AiInferenceService(cacheMgr, localMetrics, costMetrics, router, budget);
        // Inject self-proxy (same instance for unit test — @Cacheable won't fire via
        // plain self, but the pre-check still correctly sees cache empty = miss)
        injectSelfProxy(svc, svc);

        svc.analyzeAqiWithMetrics("HCM-D1", 75.0);

        assertThat(localMetrics.missCount()).isEqualTo(1.0);
        assertThat(localMetrics.hitCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Cache HIT: hit counter incremented when key already in cache before call")
    void analyzeAqiWithMetrics_cacheHit_incrementsHitCounter() {
        CacheManager cacheMgr = new ConcurrentMapCacheManager("ai-responses");
        AiCacheMetrics localMetrics = new AiCacheMetrics(new SimpleMeterRegistry());
        ModelRouter router = new ModelRouter();
        TokenBudgetService budget = mock(TokenBudgetService.class);
        when(budget.isWithinBudget(anyLong())).thenReturn(true);

        // Pre-populate the cache to simulate a warm cache hit
        AiAnalysisResponse cached = new AiAnalysisResponse("HCM-D1", "51-100", "Good air quality.", "haiku", 0L);
        cacheMgr.getCache("ai-responses").put("HCM-D1:51-100", cached);

        AiInferenceService svc = new AiInferenceService(cacheMgr, localMetrics,
                new AiCostMetrics(new SimpleMeterRegistry()), router, budget);
        injectSelfProxy(svc, svc);

        svc.analyzeAqiWithMetrics("HCM-D1", 75.0);

        assertThat(localMetrics.hitCount()).isEqualTo(1.0);
        assertThat(localMetrics.missCount()).isEqualTo(0.0);
    }

    // ─── Expected hit rate with warm cache ────────────────────────────────────

    /**
     * Verifies the ≥50% hit rate claim:
     * With TTL=5 min and polling every 60s, 5 requests arrive per TTL window.
     * First request = miss; requests 2-5 = hits → 4/5 = 80% hit rate per window.
     *
     * This test simulates 5 sequential calls for the same district+AQI bucket
     * and asserts hits/(hits+misses) ≥ 0.5.
     */
    @Test
    @DisplayName("Warm cache scenario: 5 calls same bucket → hit rate ≥ 50% (expected ~80%)")
    void warmCacheScenario_hitRateAbove50Percent() {
        CacheManager cacheMgr = new ConcurrentMapCacheManager("ai-responses");
        AiCacheMetrics localMetrics = new AiCacheMetrics(new SimpleMeterRegistry());
        ModelRouter router = new ModelRouter();
        TokenBudgetService budget = mock(TokenBudgetService.class);
        when(budget.isWithinBudget(anyLong())).thenReturn(false); // force fallback response

        AiInferenceService svc = new AiInferenceService(cacheMgr, localMetrics,
                new AiCostMetrics(new SimpleMeterRegistry()), router, budget);
        // For this test, use a spy so @Cacheable-equivalent behaviour is simulated manually
        // by pre-populating cache after first call.
        injectSelfProxy(svc, svc);

        // Simulate: first call (miss) populates cache, subsequent 4 calls are hits
        svc.analyzeAqiWithMetrics("HCM-D1", 75.0); // miss
        // Manually populate cache as @Cacheable would in a Spring proxy context
        AiAnalysisResponse resp = new AiAnalysisResponse("HCM-D1", "51-100", "Advisory text.", "none", 0L);
        cacheMgr.getCache("ai-responses").put("HCM-D1:51-100", resp);

        svc.analyzeAqiWithMetrics("HCM-D1", 80.0);  // hit (same bucket 51-100)
        svc.analyzeAqiWithMetrics("HCM-D1", 65.0);  // hit
        svc.analyzeAqiWithMetrics("HCM-D1", 99.0);  // hit
        svc.analyzeAqiWithMetrics("HCM-D1", 72.0);  // hit

        double total   = localMetrics.hitCount() + localMetrics.missCount();
        double hitRate = localMetrics.hitCount() / total;

        // 4 hits / 5 total = 80% — well above the ≥50% target
        assertThat(hitRate).isGreaterThanOrEqualTo(0.5);
        assertThat(localMetrics.hitCount()).isEqualTo(4.0);
        assertThat(localMetrics.missCount()).isEqualTo(1.0);
    }

    // ─── AiAnalysisResponse fallback ─────────────────────────────────────────

    @Test
    @DisplayName("AiAnalysisResponse.fallback returns non-null with expected fields")
    void fallbackResponse_hasExpectedFields() {
        AiAnalysisResponse resp = AiAnalysisResponse.fallback("HCM-D3", "101-150");
        assertThat(resp.districtCode()).isEqualTo("HCM-D3");
        assertThat(resp.aqiRange()).isEqualTo("101-150");
        assertThat(resp.modelUsed()).isEqualTo("none");
        assertThat(resp.recommendation()).isNotBlank();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Injects a {@code self} proxy reference into {@link AiInferenceService} via
     * reflection. In production, Spring's {@code @Autowired @Lazy} wires the real
     * AOP proxy; in unit tests we inject the plain instance (cache won't be
     * intercepted, but hit/miss logic based on pre-check still works correctly).
     */
    private static void injectSelfProxy(AiInferenceService target, AiInferenceService proxy) {
        try {
            java.lang.reflect.Field selfField = AiInferenceService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(target, proxy);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot inject self proxy for test", e);
        }
    }
}
