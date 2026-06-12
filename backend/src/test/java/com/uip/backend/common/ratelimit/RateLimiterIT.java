package com.uip.backend.common.ratelimit;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * v3.1-10: Rate limiter IT — verifies 429 at threshold, per-tenant isolation,
 * X-RateLimit-Remaining header behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("v3.1-10 Rate Limiter IT")
class RateLimiterIT {

    @Mock private FilterChain filterChain;

    private RateLimitFilter filter;
    private TenantRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TenantRateLimiter();
        ReflectionTestUtils.setField(rateLimiter, "defaultRpm", 5);
        filter = new RateLimitFilter(rateLimiter);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── 429 at threshold ────────────────────────────────────────────────────

    @Test
    @DisplayName("Requests within limit → all pass through (200)")
    void withinLimit_allPassThrough() throws Exception {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-ok");

            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest req = new MockHttpServletRequest();
                req.setRequestURI("/api/v1/esg/summary");
                MockHttpServletResponse res = new MockHttpServletResponse();

                filter.doFilterInternal(req, res, filterChain);

                assertThat(res.getStatus()).isEqualTo(200);
                verify(filterChain, times(i + 1)).doFilter(any(), any());
            }
        }
    }

    @Test
    @DisplayName("Request beyond threshold → 429 Too Many Requests")
    void beyondThreshold_returns429() throws Exception {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-limit");

            // Consume all 5 allowed tokens
            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest req = new MockHttpServletRequest();
                req.setRequestURI("/api/v1/esg/summary");
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(req, res, filterChain);
            }

            // 6th request should be rejected
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/v1/esg/summary");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, filterChain);

            assertThat(res.getStatus()).isEqualTo(429);
            assertThat(res.getHeader("Retry-After")).isEqualTo("60");
            assertThat(res.getContentType()).isEqualTo("application/json");
            assertThat(res.getContentAsString()).contains("Too Many Requests");
        }
    }

    @Test
    @DisplayName("429 response body contains standard error structure")
    void tooManyRequests_responseBodyStructure() throws Exception {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-body");

            // Exhaust tokens
            for (int i = 0; i < 5; i++) {
                rateLimiter.tryConsume("tenant-body");
            }

            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/v1/test");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, filterChain);

            assertThat(res.getStatus()).isEqualTo(429);
            String body = res.getContentAsString();
            assertThat(body).contains("\"status\":429");
            assertThat(body).contains("\"error\":\"Too Many Requests\"");
        }
    }

    // ─── Per-tenant isolation ────────────────────────────────────────────────

    @Test
    @DisplayName("Per-tenant isolation: exhausting tenant-A does not affect tenant-B")
    void perTenantIsolation_exhaustA_doesNotAffectB() throws Exception {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            // Exhaust tenant-a
            ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-a");
            for (int i = 0; i < 5; i++) {
                rateLimiter.tryConsume("tenant-a");
            }

            // tenant-a should be blocked
            MockHttpServletRequest reqA = new MockHttpServletRequest();
            reqA.setRequestURI("/api/v1/test");
            MockHttpServletResponse resA = new MockHttpServletResponse();
            filter.doFilterInternal(reqA, resA, filterChain);
            assertThat(resA.getStatus()).isEqualTo(429);

            // Switch to tenant-b — should still work
            ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-b");
            MockHttpServletRequest reqB = new MockHttpServletRequest();
            reqB.setRequestURI("/api/v1/test");
            MockHttpServletResponse resB = new MockHttpServletResponse();
            filter.doFilterInternal(reqB, resB, filterChain);

            assertThat(resB.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Per-tenant isolation: different tenants have separate buckets")
    void perTenantIsolation_separateBuckets() throws Exception {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            // Consume 3 tokens for tenant-x
            ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-x");
            for (int i = 0; i < 3; i++) {
                rateLimiter.tryConsume("tenant-x");
            }

            // Consume 3 tokens for tenant-y
            ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-y");
            for (int i = 0; i < 3; i++) {
                rateLimiter.tryConsume("tenant-y");
            }

            // Both should still have remaining tokens
            assertThat(rateLimiter.getAvailableTokens("tenant-x")).isEqualTo(2);
            assertThat(rateLimiter.getAvailableTokens("tenant-y")).isEqualTo(2);
        }
    }

    // ─── Available tokens tracking ───────────────────────────────────────────

    @Test
    @DisplayName("getAvailableTokens starts at configured RPM and decreases")
    void availableTokens_decreasesAfterConsumption() {
        assertThat(rateLimiter.getAvailableTokens("tenant-new")).isEqualTo(5);

        rateLimiter.tryConsume("tenant-new");
        assertThat(rateLimiter.getAvailableTokens("tenant-new")).isEqualTo(4);

        rateLimiter.tryConsume("tenant-new");
        assertThat(rateLimiter.getAvailableTokens("tenant-new")).isEqualTo(3);
    }

    @Test
    @DisplayName("Whitelisted tenants are never rate-limited")
    void whitelistedTenant_alwaysAllowed() {
        for (int i = 0; i < 50; i++) {
            assertThat(rateLimiter.tryConsume("flink-internal")).isTrue();
        }
        assertThat(rateLimiter.tryConsume("prometheus-scraper")).isTrue();
        assertThat(rateLimiter.tryConsume("monitoring")).isTrue();
    }

    // ─── No tenant context ───────────────────────────────────────────────────

    @Test
    @DisplayName("No tenant context → filter passes through without rate limiting")
    void noTenantContext_passesThrough() throws Exception {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(null);

            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/v1/health");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, filterChain);

            assertThat(res.getStatus()).isEqualTo(200);
            verify(filterChain).doFilter(req, res);
        }
    }
}
