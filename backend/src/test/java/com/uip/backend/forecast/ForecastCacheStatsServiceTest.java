package com.uip.backend.forecast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ForecastCacheStatsService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForecastCacheStatsService — unit")
class ForecastCacheStatsServiceTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache forecastCache;

    private ForecastCacheStatsService cacheStatsService;

    @BeforeEach
    void setUp() {
        cacheStatsService = new ForecastCacheStatsService(cacheManager);
    }

    // ---------------------------------------------------------------------------
    // getCacheStats
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getCacheStats — returns CACHE_NOT_FOUND when cache is null")
    void getCacheStats_cacheNull_returnsCacheNotFound() {
        when(cacheManager.getCache("forecasts")).thenReturn(null);

        Map<String, Object> stats = cacheStatsService.getCacheStats();

        assertThat(stats.get("status")).isEqualTo("CACHE_NOT_FOUND");
    }

    @Test
    @DisplayName("getCacheStats — returns cacheName when cache exists")
    void getCacheStats_cacheExists_returnsCacheName() {
        Object nativeCache = new Object(); // non-Redis native cache
        when(cacheManager.getCache("forecasts")).thenReturn(forecastCache);
        when(forecastCache.getNativeCache()).thenReturn(nativeCache);

        Map<String, Object> stats = cacheStatsService.getCacheStats();

        assertThat(stats.get("cacheName")).isEqualTo("forecasts");
        assertThat(stats.get("type")).isEqualTo("Object");
    }

    @Test
    @DisplayName("getCacheStats — reports type as class simple name for non-Redis cache")
    void getCacheStats_nonRedisCache_reportsSimpleClassName() {
        java.util.concurrent.ConcurrentHashMap<Object, Object> nativeCache =
                new java.util.concurrent.ConcurrentHashMap<>();
        when(cacheManager.getCache("forecasts")).thenReturn(forecastCache);
        when(forecastCache.getNativeCache()).thenReturn(nativeCache);

        Map<String, Object> stats = cacheStatsService.getCacheStats();

        assertThat(stats).containsKey("type");
        assertThat(stats.get("type")).isEqualTo("ConcurrentHashMap");
    }

    // ---------------------------------------------------------------------------
    // logEviction
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("logEviction — evicts key when cache exists")
    void logEviction_cacheExists_evictsKey() {
        when(cacheManager.getCache("forecasts")).thenReturn(forecastCache);

        cacheStatsService.logEviction("hcm:B1:30", "NEW_DATA");

        verify(forecastCache).evict("hcm:B1:30");
    }

    @Test
    @DisplayName("logEviction — does nothing when cache is null")
    void logEviction_cacheNull_doesNotThrow() {
        when(cacheManager.getCache("forecasts")).thenReturn(null);

        // Should not throw
        cacheStatsService.logEviction("hcm:B1:30", "reason");

        verify(forecastCache, never()).evict(any());
    }

    @Test
    @DisplayName("logEviction — skips eviction when cacheKey is null")
    void logEviction_nullKey_skipsEviction() {
        when(cacheManager.getCache("forecasts")).thenReturn(forecastCache);

        cacheStatsService.logEviction(null, "reason");

        verify(forecastCache, never()).evict(any());
    }

    // ---------------------------------------------------------------------------
    // evictAll
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("evictAll — calls clear on the forecast cache")
    void evictAll_cacheExists_callsClear() {
        when(cacheManager.getCache("forecasts")).thenReturn(forecastCache);

        cacheStatsService.evictAll();

        verify(forecastCache).clear();
    }

    @Test
    @DisplayName("evictAll — does nothing when cache is null")
    void evictAll_cacheNull_doesNotThrow() {
        when(cacheManager.getCache("forecasts")).thenReturn(null);

        // Should not throw
        cacheStatsService.evictAll();

        verify(forecastCache, never()).clear();
    }
}
