package com.uip.backend.common.ratelimit;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final TenantRateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            chain.doFilter(request, response);
            return;
        }

        if (!rateLimiter.tryConsume(tenantId)) {
            log.warn("Rate limit exceeded tenant={} uri={}", tenantId, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded for tenant\"}"
            );
            return;
        }
        chain.doFilter(request, response);
    }
}
