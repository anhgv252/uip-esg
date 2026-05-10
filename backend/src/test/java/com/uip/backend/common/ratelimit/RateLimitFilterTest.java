package com.uip.backend.common.ratelimit;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock TenantRateLimiter rateLimiter;
    @Mock FilterChain chain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiter);
    }

    @Test
    void doFilter_noTenant_passesThrough() throws Exception {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(null);
            MockHttpServletRequest req = new MockHttpServletRequest();
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, chain);

            verify(chain).doFilter(req, res);
            verifyNoInteractions(rateLimiter);
        }
    }

    @Test
    void doFilter_withinLimit_passesThrough() throws Exception {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-ok");
            when(rateLimiter.tryConsume("tenant-ok")).thenReturn(true);

            MockHttpServletRequest req = new MockHttpServletRequest();
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, chain);

            verify(chain).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void doFilter_exceededLimit_returns429() throws Exception {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-x");
            when(rateLimiter.tryConsume("tenant-x")).thenReturn(false);

            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/v1/test");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(429);
            assertThat(res.getHeader("Retry-After")).isEqualTo("60");
            assertThat(res.getContentType()).isEqualTo("application/json");
            verify(chain, never()).doFilter(any(), any());
        }
    }
}
