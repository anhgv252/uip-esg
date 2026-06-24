package com.uip.analytics.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Test-only security configuration for Pact provider verification.
 *
 * <p><b>Loading:</b> This config is {@code @Import}-ed <b>only</b> by
 * {@code AnalyticsServiceProviderPactTest} (together with the class-level
 * property {@code uip.security.enabled=false}). The production
 * {@link SecurityConfig} is gated by
 * {@code @ConditionalOnProperty(name = "uip.security.enabled", matchIfMissing = true)},
 * so it is skipped only when that property is set — which happens on the Pact
 * provider test class alone, never in production or in other tests. The
 * existing {@code AnalyticsControllerTest} (@WebMvcTest) keeps the real
 * security chain because it neither sets the property nor imports this config.
 *
 * <p><b>Why stub security instead of mocking {@code JwtDecoder}:</b>
 * Pact provider verification should verify <b>contract shape</b> —
 * request/response paths, status codes, body structure — not authentication.
 * Auth is verified separately by {@code AnalyticsControllerTest} (401/403
 * assertions) and Keycloak integration tests. Per
 * {@code feedback_sprint4_pain_points} ("@WebMvcTest + Security filter"),
 * contract tests must stub the security filter chain rather than fight the
 * production JWT filter.
 *
 * <p><b>Method security ({@code @PreAuthorize}):</b>
 * The analytics controller uses {@code @PreAuthorize("hasAnyRole(...)")}. A bare
 * {@code permitAll} filter chain does NOT populate the {@code SecurityContext},
 * so method security would still reject unauthenticated requests. A filter
 * stamps every request with an authenticated principal carrying the roles the
 * controller expects ({@code ROLE_ADMIN}, {@code ROLE_OPERATOR},
 * {@code ROLE_ANALYTICS_READ}).
 *
 * <p>The {@code @Profile("test")} guard is belt-and-braces: this class lives in
 * {@code src/test/java} (never packaged) and is only ever imported explicitly.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("test")
public class TestSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(TestSecurityConfig.class);

    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        log.debug("TEST security active — permitAll + stamped principal for Pact provider verification");
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // Stamp an authenticated principal before the controller's
            // @PreAuthorize checks evaluate. Stamped authorities cover every
            // role used by AnalyticsController so method security passes.
            .addFilterBefore(stampAuthenticatedPrincipal(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public OncePerRequestFilter stampAuthenticatedPrincipal() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                    FilterChain chain) throws ServletException, IOException {
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_OPERATOR"),
                        new SimpleGrantedAuthority("ROLE_ANALYTICS_READ"));
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken("pact-test", null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                chain.doFilter(req, res);
            }
        };
    }
}
