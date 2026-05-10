package com.uip.backend.common.ratelimit;

import com.uip.backend.tenant.domain.TenantConfigEntry;
import com.uip.backend.tenant.repository.TenantConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantRateLimiterTest {

    private TenantRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TenantRateLimiter();
        ReflectionTestUtils.setField(rateLimiter, "defaultRpm", 10);
    }

    @Test
    void tryConsume_whitelistedTenant_alwaysAllowed() {
        for (int i = 0; i < 20; i++) {
            assertThat(rateLimiter.tryConsume("flink-internal")).isTrue();
        }
    }

    @Test
    void tryConsume_prometheusScraperWhitelisted() {
        assertThat(rateLimiter.tryConsume("prometheus-scraper")).isTrue();
    }

    @Test
    void tryConsume_monitoringWhitelisted() {
        assertThat(rateLimiter.tryConsume("monitoring")).isTrue();
    }

    @Test
    void tryConsume_normalTenant_consumesTokens() {
        assertThat(rateLimiter.tryConsume("tenant-a")).isTrue();
    }

    @Test
    void getAvailableTokens_normalTenant_returnsPositive() {
        long tokens = rateLimiter.getAvailableTokens("tenant-b");
        assertThat(tokens).isGreaterThan(0);
    }

    @Test
    void tryConsume_exhaustsBucket_returnsFalse() {
        // consume all 10 tokens
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume("tenant-exhaust");
        }
        assertThat(rateLimiter.tryConsume("tenant-exhaust")).isFalse();
    }

    @Test
    void tryConsume_differentTenants_separateBuckets() {
        // exhaust tenant-c
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume("tenant-c");
        }
        // tenant-d should still have tokens
        assertThat(rateLimiter.tryConsume("tenant-d")).isTrue();
    }

    @Test
    void getAvailableTokens_afterConsume_decreases() {
        rateLimiter.tryConsume("tenant-e");
        long remaining = rateLimiter.getAvailableTokens("tenant-e");
        assertThat(remaining).isEqualTo(9);
    }

    // ─── Redis path tests ────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_redisAvailable_usesRedisScript() {
        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
        when(mockRedis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
        ReflectionTestUtils.setField(rateLimiter, "redisTemplate", mockRedis);

        assertThat(rateLimiter.tryConsume("tenant-redis")).isTrue();
        verify(mockRedis).execute(any(RedisScript.class), anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_redisReturnsCountAboveLimit_returnsFalse() {
        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
        when(mockRedis.execute(any(RedisScript.class), anyList())).thenReturn(11L); // 11 > rpm=10
        ReflectionTestUtils.setField(rateLimiter, "redisTemplate", mockRedis);

        assertThat(rateLimiter.tryConsume("tenant-over")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_redisThrowsException_fallsBackToInMemory() {
        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
        when(mockRedis.execute(any(RedisScript.class), anyList()))
                .thenThrow(new RuntimeException("Redis connection error"));
        ReflectionTestUtils.setField(rateLimiter, "redisTemplate", mockRedis);

        assertThat(rateLimiter.tryConsume("tenant-fallback")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_redisReturnsNull_fallsBackToInMemory() {
        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
        when(mockRedis.execute(any(RedisScript.class), anyList())).thenReturn(null);
        ReflectionTestUtils.setField(rateLimiter, "redisTemplate", mockRedis);

        assertThat(rateLimiter.tryConsume("tenant-null")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAvailableTokens_redisAvailable_returnsRemainingTokens() {
        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(mockRedis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("3"); // used 3 of rpm=10
        ReflectionTestUtils.setField(rateLimiter, "redisTemplate", mockRedis);

        assertThat(rateLimiter.getAvailableTokens("tenant-avail")).isEqualTo(7);
    }

    // ─── Per-tenant RPM tests ─────────────────────────────────────────────────

    @Test
    void reloadTenantRpm_withNullRepository_doesNothing() {
        // tenantConfigRepository is null by default in unit tests — should not throw
        rateLimiter.reloadTenantRpm();
        assertThat(rateLimiter.tryConsume("tenant-ok")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_perTenantRpm_usesConfiguredLimit() {
        TenantConfigRepository mockRepo = mock(TenantConfigRepository.class);
        TenantConfigEntry entry = new TenantConfigEntry();
        entry.setTenantId("tenant-limited");
        entry.setConfigKey("rate-limit.requests-per-minute");
        entry.setConfigValue("3"); // tenant-specific limit of 3
        when(mockRepo.findAllByConfigKey("rate-limit.requests-per-minute"))
                .thenReturn(List.of(entry));
        ReflectionTestUtils.setField(rateLimiter, "tenantConfigRepository", mockRepo);
        rateLimiter.reloadTenantRpm();

        // First 3 should pass (in-memory bucket with capacity 3)
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryConsume("tenant-limited")).isTrue();
        }
        // 4th request should be denied
        assertThat(rateLimiter.tryConsume("tenant-limited")).isFalse();
        // Other tenants are unaffected (still use defaultRpm=10)
        assertThat(rateLimiter.tryConsume("tenant-other")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tryConsume_perTenantRpm_redisUsesConfiguredLimit() {
        TenantConfigRepository mockRepo = mock(TenantConfigRepository.class);
        TenantConfigEntry entry = new TenantConfigEntry();
        entry.setTenantId("tenant-redis-limited");
        entry.setConfigKey("rate-limit.requests-per-minute");
        entry.setConfigValue("5");
        when(mockRepo.findAllByConfigKey("rate-limit.requests-per-minute"))
                .thenReturn(List.of(entry));
        ReflectionTestUtils.setField(rateLimiter, "tenantConfigRepository", mockRepo);
        rateLimiter.reloadTenantRpm();

        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
        when(mockRedis.execute(any(RedisScript.class), anyList())).thenReturn(6L); // 6 > rpm=5
        ReflectionTestUtils.setField(rateLimiter, "redisTemplate", mockRedis);

        assertThat(rateLimiter.tryConsume("tenant-redis-limited")).isFalse();
    }

    @Test
    void reloadTenantRpm_invalidValue_skipsEntry() {
        TenantConfigRepository mockRepo = mock(TenantConfigRepository.class);
        TenantConfigEntry bad = new TenantConfigEntry();
        bad.setTenantId("tenant-bad");
        bad.setConfigKey("rate-limit.requests-per-minute");
        bad.setConfigValue("not-a-number");
        when(mockRepo.findAllByConfigKey("rate-limit.requests-per-minute"))
                .thenReturn(List.of(bad));
        ReflectionTestUtils.setField(rateLimiter, "tenantConfigRepository", mockRepo);

        // Should not throw; bad entry is skipped → tenant falls back to defaultRpm
        rateLimiter.reloadTenantRpm();
        assertThat(rateLimiter.tryConsume("tenant-bad")).isTrue();
    }
}
