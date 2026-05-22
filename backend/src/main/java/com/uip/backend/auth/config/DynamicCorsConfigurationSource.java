package com.uip.backend.auth.config;

import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.repository.TenantConfigRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloads allowed CORS origins from tenant_config (key=cors.allowed-origins) every 5 minutes.
 * Per-request tenant-aware: only origins configured for the current tenant are allowed,
 * preventing cross-tenant CORS leakage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicCorsConfigurationSource implements CorsConfigurationSource {

    private static final List<String> DEV_ORIGINS = List.of(
            "http://localhost:3000", "http://localhost:3001", "http://localhost:5173"
    );
    // Special key for origins that apply to all tenants (dev + env-var overrides)
    private static final String GLOBAL_KEY = "_global";

    private final TenantConfigRepository tenantConfigRepository;
    // tenantId -> allowed origins; GLOBAL_KEY holds dev + env-var origins (apply to all tenants)
    private final AtomicReference<Map<String, Set<String>>> tenantOrigins =
            new AtomicReference<>(Map.of(GLOBAL_KEY, new HashSet<>(DEV_ORIGINS)));

    @PostConstruct
    public void init() {
        reload();
    }

    @Scheduled(fixedDelay = 300_000)
    @Transactional(readOnly = true)
    public void reload() {
        Map<String, Set<String>> map = new HashMap<>();
        Set<String> globalOrigins = new HashSet<>(DEV_ORIGINS);
        String envOrigin = System.getenv().getOrDefault("CORS_ALLOWED_ORIGIN", "");
        if (!envOrigin.isBlank()) globalOrigins.add(envOrigin.trim());
        map.put(GLOBAL_KEY, Set.copyOf(globalOrigins));
        tenantConfigRepository.findAllByConfigKey("cors.allowed-origins").forEach(entry ->
                map.put(entry.getTenantId(), Set.copyOf(new HashSet<>(parseOrigins(entry.getConfigValue()))))
        );
        tenantOrigins.set(Map.copyOf(map));
        log.debug("CORS origins reloaded: {} tenant-specific configs", map.size() - 1);
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) return null;
        Map<String, Set<String>> snapshot = tenantOrigins.get();
        boolean allowed = snapshot.getOrDefault(GLOBAL_KEY, Set.of()).contains(origin);
        if (!allowed) {
            String tenant = TenantContext.getCurrentTenant();
            if (tenant != null) {
                allowed = snapshot.getOrDefault(tenant, Set.of()).contains(origin);
            }
        }
        if (!allowed) return null;
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin(origin);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Tenant-Override"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }

    // package-private for testing
    Map<String, Set<String>> getTenantOrigins() {
        return tenantOrigins.get();
    }

    private List<String> parseOrigins(String value) {
        // Accepts CSV or JSON array: ["https://a.com","https://b.com"] or https://a.com,https://b.com
        String cleaned = value.trim().replaceAll("^\\[|]$", "").replace("\"", "");
        return List.of(cleaned.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
