package com.uip.backend.forecast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Exposes cache statistics for the forecast cache.
 * Used by the stats endpoint and Kafka eviction listener.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastCacheStatsService {

    private final CacheManager cacheManager;

    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        Cache forecastCache = cacheManager.getCache("forecasts");
        if (forecastCache == null) {
            stats.put("status", "CACHE_NOT_FOUND");
            return stats;
        }

        Object nativeCache = forecastCache.getNativeCache();
        stats.put("cacheName", "forecasts");

        if (nativeCache instanceof org.springframework.data.redis.cache.RedisCache) {
            stats.put("type", "redis");
        } else {
            stats.put("type", nativeCache.getClass().getSimpleName());
        }

        return stats;
    }

    /**
     * Log cache eviction event — called when forecast data changes
     * and cached entries need invalidation.
     */
    public void logEviction(String cacheKey, String reason) {
        log.info("Forecast cache eviction: key={}, reason={}", cacheKey, reason);
        Cache forecastCache = cacheManager.getCache("forecasts");
        if (forecastCache != null && cacheKey != null) {
            forecastCache.evict(cacheKey);
            log.info("Evicted forecast cache entry: {}", cacheKey);
        }
    }

    /**
     * Evict all forecast cache entries — called on bulk data changes.
     */
    public void evictAll() {
        log.warn("Evicting ALL forecast cache entries");
        Cache forecastCache = cacheManager.getCache("forecasts");
        if (forecastCache != null) {
            forecastCache.clear();
        }
    }
}
