package com.uip.backend.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.util.List;

/**
 * Separate security filter chain for Camunda webapp and engine-rest.
 * Camunda webapp is session-based (form login), unlike our stateless JWT API.
 * This must be processed BEFORE the API filter chain ({@code @Order(1)} vs {@code @Order(2)}).
 *
 * Uses AntPathRequestMatcher explicitly to avoid MvcRequestMatcher dependency
 * (which requires Spring MVC and breaks @SpringBootTest(webEnvironment=NONE)).
 */
@Configuration
public class CamundaSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain camundaFilterChain(HttpSecurity http) throws Exception {
        var camundaMatcher = new OrRequestMatcher(List.of(
                new AntPathRequestMatcher("/camunda/**"),
                new AntPathRequestMatcher("/engine-rest/**"),
                new AntPathRequestMatcher("/lib/**"),
                new AntPathRequestMatcher("/api/cockpit/**"),
                new AntPathRequestMatcher("/api/tasklist/**")
        ));

        http
            .securityMatcher(camundaMatcher)
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    AntPathRequestMatcher.antMatcher("/camunda/app/*/styles/**"),
                    AntPathRequestMatcher.antMatcher("/camunda/app/*/fonts/**"),
                    AntPathRequestMatcher.antMatcher("/camunda/app/*/images/**"),
                    AntPathRequestMatcher.antMatcher("/camunda/app/*/scripts/**"),
                    AntPathRequestMatcher.antMatcher("/camunda/api/admin/auth/user/*/login/*"),
                    AntPathRequestMatcher.antMatcher("/camunda/api/cockpit/auth/user/*/login/*"),
                    AntPathRequestMatcher.antMatcher("/camunda/api/tasklist/auth/user/*/login/*")
                ).permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
