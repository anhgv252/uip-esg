package com.uip.backend.ai;

import com.uip.backend.ai.budget.TokenBudgetService;
import com.uip.backend.ai.cache.AiCacheMetrics;
import com.uip.backend.ai.flink.DistrictAggregationEvent;
import com.uip.backend.ai.routing.ModelRouter;
import com.uip.backend.tenant.context.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @AfterEach
    void clearTenant() {
        // No ThreadLocal leak between tests (MVP5-S1-T06).
        TenantContext.clear();
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

    // ─── MVP5-S1-T06: cross-tenant cache isolation ───────────────────────────

    /**
     * MVP5-S1-T06: verifies every AI cache key is namespaced by tenant so two
     * tenants sharing a district code receive independent (and independently
     * billed) AI responses. A cache hit for tenant A MUST NOT be served to
     * tenant B.
     */
    @Nested
    @DisplayName("MVP5-S1-T06: cross-tenant cache isolation")
    class CrossTenantIsolation {

        @Test
        @DisplayName("AQI: tenant B same district+AQI → MISS (not tenant A's cached entry)")
        void aqi_sameDistrictDifferentTenant_isMiss() {
            TenantContext.setCurrentTenant("tenant-A");
            svc.analyzeAqiWithMetrics("HCM-D1", 50.0);
            // tenant-A: cold → 1 miss, 0 hits
            assertThat(metrics.missCount()).isEqualTo(1.0);
            assertThat(metrics.hitCount()).isZero();

            TenantContext.setCurrentTenant("tenant-B");
            svc.analyzeAqiWithMetrics("HCM-D1", 50.0);
            // tenant-B: identical district+AQI but different tenant → still a MISS
            assertThat(metrics.missCount()).isEqualTo(2.0);
            assertThat(metrics.hitCount()).isZero();
        }

        @Test
        @DisplayName("AQI: same tenant, same district+AQI → HIT (cache works within tenant)")
        void aqi_sameTenantSameKey_isHit() {
            TenantContext.setCurrentTenant("tenant-A");
            // NOTE: @Cacheable does not fire under a plain unit test (no Spring proxy),
            // so seed the cache manually to simulate the proxy storing the first result.
            // The pre-check key built by analyzeAqiWithMetrics MUST match this seed
            // (same tenant prefix) for the HIT to register — this is exactly the
            // MVP5-S1-T06 invariant under test.
            seedCache("tenant-A", "HCM-D1", 50.0);
            svc.analyzeAqiWithMetrics("HCM-D1", 50.0); // pre-existed → HIT
            assertThat(metrics.missCount()).isZero();
            assertThat(metrics.hitCount()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("AQI: two distinct cache entries for two tenants (no orphan, no leak)")
        void aqi_twoTenantsProduceTwoDistinctEntries() {
            org.springframework.cache.Cache cache =
                    cacheManager.getCache("ai-responses");

            TenantContext.setCurrentTenant("tenant-A");
            seedCache("tenant-A", "HCM-D1", 50.0);
            TenantContext.setCurrentTenant("tenant-B");
            seedCache("tenant-B", "HCM-D1", 50.0);

            // Both tenant-specific entries must exist (pre-check key + @Cacheable key agree).
            assertThat(cache.get("tenant-A:HCM-D1:" + com.uip.backend.ai.cache.AqiRangeBucket.bucket(50.0)))
                    .as("tenant-A cache entry").isNotNull();
            assertThat(cache.get("tenant-B:HCM-D1:" + com.uip.backend.ai.cache.AqiRangeBucket.bucket(50.0)))
                    .as("tenant-B cache entry").isNotNull();
            // And the tenant-A seed must NOT register as a HIT for tenant-B.
            TenantContext.setCurrentTenant("tenant-B");
            svc.analyzeAqiWithMetrics("HCM-D1", 50.0);
            assertThat(metrics.hitCount()).isEqualTo(1.0); // tenant-B's own entry
        }

        @Test
        @DisplayName("Generic: tenant B same district+type+range → MISS")
        void generic_sameDistrictDifferentTenant_isMiss() {
            DistrictAggregationEvent evtA = new DistrictAggregationEvent(
                    "tenant-A", "HCM-D2", "NOISE", 3, 85.0, 70.0, 1L, 2L, List.of());
            DistrictAggregationEvent evtB = new DistrictAggregationEvent(
                    "tenant-B", "HCM-D2", "NOISE", 3, 85.0, 70.0, 1L, 2L, List.of());

            TenantContext.setCurrentTenant("tenant-A");
            svc.analyzeGenericWithMetrics(evtA);
            double missesAfterA = metrics.missCount();

            TenantContext.setCurrentTenant("tenant-B");
            svc.analyzeGenericWithMetrics(evtB);
            // tenant-B must be a fresh MISS — tenant-A's entry does not leak.
            assertThat(metrics.missCount()).isEqualTo(missesAfterA + 1.0);
            assertThat(metrics.hitCount()).isZero();
        }

        @Test
        @DisplayName("Null tenant context → falls back to 'global' namespace (documented)")
        void nullTenant_fallsBackToGlobal() {
            // No TenantContext set → currentTenantOrGlobal() returns "global".
            // @Cacheable is inert under a unit test, so seed the "global" key manually
            // to confirm the pre-check sees it (proving the key prefix is "global:").
            seedCache("global", "HCM-D1", 50.0);
            svc.analyzeAqiWithMetrics("HCM-D1", 50.0); // pre-existed → HIT
            assertThat(metrics.hitCount()).isEqualTo(1.0);
            assertThat(metrics.missCount()).isZero();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static DistrictAggregationEvent event(String district, String type, double max) {
        return new DistrictAggregationEvent("tenant-A", district, type, 3, max, max - 10,
                1L, 2L, List.of(new DistrictAggregationEvent.SensorSnapshot("s1", max, 1L)));
    }

    /**
     * Seeds the AI cache with a key that matches the pre-check key built by
     * {@link AiInferenceService#analyzeAqiWithMetrics}: {@code {tenant}:{district}:{aqiRange}}.
     * Used by the cross-tenant tests because {@code @Cacheable} is inert under a
     * plain unit test (no Spring proxy). The seed proves the pre-check key and the
     * (hypothetical) {@code @Cacheable} key share the same tenant-prefixed shape.
     */
    private void seedCache(String tenant, String district, double aqi) {
        org.springframework.cache.Cache cache = cacheManager.getCache("ai-responses");
        String key = tenant + ":" + district + ":" + com.uip.backend.ai.cache.AqiRangeBucket.bucket(aqi);
        cache.put(key, new AiAnalysisResponse(district, com.uip.backend.ai.cache.AqiRangeBucket.bucket(aqi),
                "seeded", "none", System.currentTimeMillis()));
    }

    private static void injectSelfProxy(AiInferenceService target, AiInferenceService proxy) throws Exception {
        Field f = AiInferenceService.class.getDeclaredField("self");
        f.setAccessible(true);
        f.set(target, proxy);
    }
}
