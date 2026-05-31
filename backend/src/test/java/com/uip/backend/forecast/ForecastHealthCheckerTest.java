package com.uip.backend.forecast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for ForecastHealthChecker — Python health check + cache recovery.
 *
 * 5 test cases:
 *   1. UP (no prior DOWN) → no cache clear
 *   2. DOWN → log WARN, set previouslyDown=true
 *   3. UP after DOWN → clear forecast cache (auto-recover)
 *   4. Continuous UP → no cache clear on subsequent checks
 *   5. DOWN after recovery → correctly tracks new DOWN state
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForecastHealthChecker — unit")
class ForecastHealthCheckerTest {

    private static final String BASE_URL = "http://uip-forecast-service:8090";

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache forecastsCache;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private Cursor<String> scanCursor;

    private MockRestServiceServer mockServer;
    private ForecastHealthChecker healthChecker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        healthChecker = new ForecastHealthChecker(BASE_URL, cacheManager, redisTemplate, builder);
    }

    @Test
    @DisplayName("checkHealth — UP with no prior DOWN → no cache clear")
    void checkHealth_upNoPriorDown_noCacheClear() {
        mockServer.expect(requestTo(containsString("/actuator/health")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        healthChecker.checkHealth();

        assertThat(healthChecker.isPreviouslyDown()).isFalse();
        verifyNoInteractions(cacheManager);
        verify(redisTemplate, never()).scan(any());
        mockServer.verify();
    }

    @Test
    @DisplayName("checkHealth — DOWN → sets previouslyDown, no cache clear")
    void checkHealth_down_setsPreviouslyDown() {
        mockServer.expect(requestTo(containsString("/actuator/health")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        healthChecker.checkHealth();

        assertThat(healthChecker.isPreviouslyDown()).isTrue();
        verifyNoInteractions(cacheManager);
        mockServer.verify();
    }

    @Test
    @DisplayName("checkHealth — UP after DOWN → clears forecast cache (auto-recover)")
    void checkHealth_upAfterDown_clearsForecastCache() {
        healthChecker.setPreviouslyDown(true);

        mockServer.expect(requestTo(containsString("/actuator/health")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        when(cacheManager.getCache("forecasts")).thenReturn(forecastsCache);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(scanCursor);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<String> action = inv.getArgument(0);
            action.accept("forecasts::hcm|B1|30");
            return null;
        }).when(scanCursor).forEachRemaining(any());

        healthChecker.checkHealth();

        assertThat(healthChecker.isPreviouslyDown()).isFalse();
        verify(forecastsCache).clear();
        verify(redisTemplate).scan(any(ScanOptions.class));
        verify(redisTemplate).delete(Set.of("forecasts::hcm|B1|30"));
        mockServer.verify();
    }

    @Test
    @DisplayName("checkHealth — continuous UP → no cache clear on subsequent checks")
    void checkHealth_continuousUp_noCacheClear() {
        mockServer.expect(requestTo(containsString("/actuator/health")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(containsString("/actuator/health")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        healthChecker.checkHealth();
        assertThat(healthChecker.isPreviouslyDown()).isFalse();

        healthChecker.checkHealth();
        assertThat(healthChecker.isPreviouslyDown()).isFalse();

        verifyNoInteractions(cacheManager);
        verify(redisTemplate, never()).scan(any());
        mockServer.verify();
    }

    @Test
    @DisplayName("checkHealth — DOWN after recovery → correctly tracks new DOWN state")
    void checkHealth_downAfterRecovery_tracksNewDown() {
        healthChecker.setPreviouslyDown(true);

        mockServer.expect(requestTo(containsString("/actuator/health")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(containsString("/actuator/health")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        when(cacheManager.getCache("forecasts")).thenReturn(forecastsCache);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(scanCursor);
        // scanCursor.forEachRemaining is a no-op by default — empty result, delete not called

        // First check — UP (recovery) → clears cache
        healthChecker.checkHealth();
        assertThat(healthChecker.isPreviouslyDown()).isFalse();

        // Second check — DOWN again
        healthChecker.checkHealth();
        assertThat(healthChecker.isPreviouslyDown()).isTrue();

        // Cache was cleared exactly once (during recovery only)
        verify(forecastsCache, times(1)).clear();
        mockServer.verify();
    }
}
