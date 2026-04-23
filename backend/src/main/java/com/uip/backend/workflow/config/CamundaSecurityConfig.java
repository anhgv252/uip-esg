package com.uip.backend.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Separate security filter chain for Camunda webapp and engine-rest.
 * Camunda webapp is session-based (form login), unlike our stateless JWT API.
 * This must be processed BEFORE the API filter chain ({@code @Order(1)} vs {@code @Order(2)}).
 */
@Configuration
public class CamundaSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain camundaFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/camunda/**", "/engine-rest/**", "/lib/**",
                "/api/cockpit/**", "/api/tasklist/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.disable())
            // Camunda webapp needs sessions — do NOT use STATELESS here
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .headers(headers -> headers
                // Camunda webapp uses internal iframes — must be SameOrigin, not DENY
                .frameOptions(frame -> frame.sameOrigin())
            )
            .authorizeHttpRequests(auth -> auth
                // Camunda static assets and login page — public
                .requestMatchers(
                    "/camunda/app/*/styles/**",
                    "/camunda/app/*/fonts/**",
                    "/camunda/app/*/images/**",
                    "/camunda/app/*/scripts/**",
                    "/camunda/api/admin/auth/user/*/login/*",
                    "/camunda/api/cockpit/auth/user/*/login/*",
                    "/camunda/api/tasklist/auth/user/*/login/*"
                ).permitAll()
                // All remaining Camunda paths require authentication
                .anyRequest().authenticated()
            );
        // Camunda manages its own form login — CamundaBpmProcessEngineAutoConfiguration wires authentication

        return http.build();
    }
}
