package com.uip.backend.auth.config;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reloads allowed CORS origins from tenant_config (key=cors.allowed-origins) every 5 minutes.
 * Replaces the static single-origin env-var approach — supports one origin set per tenant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicCorsConfigurationSource implements CorsConfigurationSource {

    private static final List<String> DEV_ORIGINS = List.of(
            "http://localhost:3000", "http://localhost:3001", "http://localhost:5173"
    );

    private final TenantConfigRepository tenantConfigRepository;
    private final AtomicReference<Set<String>> allowedOrigins =
            new AtomicReference<>(new HashSet<>(DEV_ORIGINS));

    @PostConstruct
    public void init() {
        reload();
    }

    @Scheduled(fixedDelay = 300_000)
    @Transactional(readOnly = true)
    public void reload() {
        Set<String> origins = new HashSet<>(DEV_ORIGINS);
        String envOrigin = System.getenv().getOrDefault("CORS_ALLOWED_ORIGIN", "");
        if (!envOrigin.isBlank()) {
            origins.add(envOrigin.trim());
        }
        tenantConfigRepository.findAllByConfigKey("cors.allowed-origins")
                .forEach(entry -> parseOrigins(entry.getConfigValue()).forEach(origins::add));
        allowedOrigins.set(Set.copyOf(origins));
        log.debug("CORS origins reloaded: {}", origins);
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.get().contains(origin)) {
            return null;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin(origin);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }

    // package-private for testing
    Set<String> getAllowedOrigins() {
        return allowedOrigins.get();
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
