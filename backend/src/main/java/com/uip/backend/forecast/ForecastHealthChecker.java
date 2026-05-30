package com.uip.backend.forecast;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/**
 * Scheduled health check for the Python forecast-service.
 *
 * When the Python service is DOWN, the naive adapter already serves requests.
 * When the Python service recovers (UP after DOWN), this clears the Redis
 * {@code forecasts::*} cache so that fresh forecasts are fetched from Python
 * instead of serving stale naive-fallback results.
 *
 * ADR-032 D6: runs only when uip.capabilities.forecast-engine=python.
 * Health endpoint: /actuator/health (Spring Boot Actuator on Python side).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "uip.capabilities.forecast-engine", havingValue = "python")
public class ForecastHealthChecker {

    private final RestClient restClient;
    private final CacheManager cacheManager;
    private final StringRedisTemplate redisTemplate;

    @Value("${uip.forecast.health-timeout-ms:5000}")
    private int healthTimeoutMs;

    private volatile boolean previouslyDown = false;

    public ForecastHealthChecker(@Value("${uip.forecast.service-url}") String baseUrl,
                                 CacheManager cacheManager,
                                 StringRedisTemplate redisTemplate,
                                 RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Health check every 5 minutes (300 seconds).
     * Initial delay 60 seconds to let the application fully start.
     */
    @Scheduled(
            fixedDelayString = "${uip.forecast.health-check-ms:300000}",
            initialDelayString = "${uip.forecast.health-initial-delay-ms:60000}"
    )
    public void checkHealth() {
        boolean isUp = pingHealthEndpoint();

        if (!isUp) {
            previouslyDown = true;
            log.warn("Python forecast-service is DOWN — naive fallback is serving requests");
            return;
        }

        if (previouslyDown) {
            log.info("Python forecast-service RECOVERED — clearing forecast cache for auto-recovery");
            clearForecastCache();
            previouslyDown = false;
        } else {
            log.debug("Python forecast-service health check: UP");
        }
    }

    /**
     * Visible for testing — returns whether the service is currently considered down.
     */
    boolean isPreviouslyDown() {
        return previouslyDown;
    }

    /**
     * Visible for testing — sets the internal state for test scenarios.
     */
    void setPreviouslyDown(boolean down) {
        this.previouslyDown = down;
    }

    private boolean pingHealthEndpoint() {
        try {
            String response = restClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .body(String.class);

            if (response != null && response.contains("\"status\"")) {
                return !response.contains("\"status\":\"DOWN\"");
            }
            // If we get any response, consider it UP
            return response != null;
        } catch (Exception e) {
            log.debug("Python forecast-service health check failed: {}", e.getMessage());
            return false;
        }
    }

    private void clearForecastCache() {
        // Strategy 1: Use Spring CacheManager abstraction (works with all cache backends)
        Cache forecastsCache = cacheManager.getCache("forecasts");
        if (forecastsCache != null) {
            forecastsCache.clear();
            log.info("Cleared 'forecasts' cache via CacheManager");
        }

        // Strategy 2: Direct Redis key scan for thorough cleanup
        // The Spring CacheManager clear() works for RedisCache, but we also scan
        // for any stray keys matching forecasts::* pattern for safety.
        try {
            var keys = redisTemplate.keys("forecasts::*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(Objects.requireNonNull(keys));
                log.info("Cleared {} Redis keys matching forecasts::*", keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis key scan for forecasts::* failed (cache may already be clear): {}", e.getMessage());
        }
    }
}
