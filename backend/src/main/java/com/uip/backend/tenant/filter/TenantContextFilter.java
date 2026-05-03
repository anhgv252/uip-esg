package com.uip.backend.tenant.filter;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Extracts tenant_id from JWT claims and sets it in TenantContext ThreadLocal.
 * Runs AFTER JwtAuthenticationFilter so the JWT has already been validated.
 *
 * ADR-021: Fallback to "default" when tenant_id claim is missing or no JWT present.
 * ADR-010: TenantContext is consumed by TenantContextAspect to run SET LOCAL app.tenant_id.
 */
@Component
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_ID_CLAIM = "tenant_id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String tenantId = extractTenantId(request);
            TenantContext.setCurrentTenant(tenantId);

            log.debug("TenantContext set: tenantId={}", TenantContext.getCurrentTenant());

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Extract tenant_id from JWT payload claims.
     * The JWT was already validated by JwtAuthenticationFilter running before this filter,
     * so we only need to decode the payload (no signature verification needed).
     * Falls back to "default" (ADR-021) if no JWT or claim is absent.
     */
    private String extractTenantId(HttpServletRequest request) {
        String jwt = resolveToken(request);
        if (jwt == null) {
            log.debug("No JWT found in request, using default tenant");
            return TenantContext.getDefaultTenant();
        }

        try {
            String payloadJson = decodeJwtPayload(jwt);
            String tenantId = extractJsonField(payloadJson, TENANT_ID_CLAIM);
            if (tenantId != null && !tenantId.isBlank()) {
                return tenantId;
            }
        } catch (Exception e) {
            log.debug("Failed to extract tenant_id from JWT payload: {}", e.getMessage());
        }

        log.debug("tenant_id claim not found in JWT, using default tenant");
        return TenantContext.getDefaultTenant();
    }

    /**
     * Resolve JWT token from Authorization header or access_token cookie.
     * Mirrors JwtAuthenticationFilter's resolution logic.
     */
    private String resolveToken(HttpServletRequest request) {
        // 1. Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. access_token cookie
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(cookie -> "access_token".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    /**
     * Decode JWT payload (middle segment) without signature verification.
     * JwtAuthenticationFilter already validated the token before this filter runs.
     */
    private String decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT structure");
        }
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        return new String(payloadBytes, StandardCharsets.UTF_8);
    }

    /**
     * Minimal JSON field extraction for simple string values.
     * Avoids pulling in a full JSON library just for one claim.
     */
    private String extractJsonField(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        start += needle.length();
        int end = json.indexOf("\"", start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }
}
