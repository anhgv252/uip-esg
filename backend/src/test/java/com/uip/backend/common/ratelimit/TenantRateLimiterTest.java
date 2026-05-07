package com.uip.backend.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

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
}
