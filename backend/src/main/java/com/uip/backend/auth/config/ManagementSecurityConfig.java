package com.uip.backend.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Security configuration for the Spring Boot management server (port 8081).
 *
 * The management server runs in a child WebApplicationContext. Spring Security's
 * DelegatingFilterProxy is NOT automatically registered in this child context, so
 * a SecurityFilterChain bean alone has no effect. We register a simple servlet
 * filter directly via FilterRegistrationBean instead.
 *
 * Access policy:
 *  - /actuator/health, /actuator/info → public (liveness probes via other URLs)
 *  - /actuator/prometheus             → HTTP Basic (prometheus scraper user)
 */
@ManagementContextConfiguration(proxyBeanMethods = false)
public class ManagementSecurityConfig {

    @Value("${uip.management.prometheus.password:prometheus-dev-scrape}")
    private String prometheusPassword;

    @Bean
    public FilterRegistrationBean<PrometheusAuthFilter> prometheusAuthFilterRegistration() {
        FilterRegistrationBean<PrometheusAuthFilter> reg = new FilterRegistrationBean<>(
                new PrometheusAuthFilter("prometheus", prometheusPassword));
        reg.addUrlPatterns("/actuator/prometheus", "/actuator/prometheus/");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    static final class PrometheusAuthFilter extends OncePerRequestFilter {

        private final String expectedAuthHeader;

        PrometheusAuthFilter(String username, String password) {
            this.expectedAuthHeader = "Basic " + Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (expectedAuthHeader.equals(authHeader)) {
                filterChain.doFilter(request, response);
            } else {
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"management\"");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
        }
    }
}
