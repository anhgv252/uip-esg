package com.uip.analytics.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${uip.security.jwt-secret}")
    private String jwtSecret;

    @Value("${uip.security.keycloak-public-key:}")
    private String keycloakPublicKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // /error phải được permit — Tomcat re-dispatch đến đây khi sendError()
                // bị chặn → Spring Security gọi AuthenticationEntryPoint → 401 sai
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Bearer token required\"}");
                })
            )
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public OncePerRequestFilter jwtAuthFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                    FilterChain chain) throws ServletException, IOException {
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    String token = header.substring(7);
                    if (!tryHmacAuth(token) && !tryRsaAuth(token)) {
                        log.debug("JWT validation failed for all algorithms");
                        res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                        return;
                    }
                }
                chain.doFilter(req, res);
            }

            private boolean tryHmacAuth(String token) {
                try {
                    JwtParser parser = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                        .build();
                    Claims claims = parser.parseSignedClaims(token).getPayload();
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) claims.get("roles", List.class);
                    // JWT từ monolith đã chứa prefix ROLE_ (e.g. "ROLE_ADMIN")
                    List<SimpleGrantedAuthority> authorities = roles == null ? List.of()
                            : roles.stream().map(SimpleGrantedAuthority::new)
                                   .collect(Collectors.toList());
                    SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
                    return true;
                } catch (Exception e) {
                    log.trace("HMAC JWT validation failed: {}", e.getMessage());
                    return false;
                }
            }

            private boolean tryRsaAuth(String token) {
                if (keycloakPublicKey == null || keycloakPublicKey.isBlank()) {
                    return false;
                }
                try {
                    String stripped = keycloakPublicKey
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                    byte[] decoded = Base64.getDecoder().decode(stripped);
                    PublicKey publicKey = KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(decoded));
                    JwtParser parser = Jwts.parser()
                        .verifyWith(publicKey)
                        .build();
                    Claims claims = parser.parseSignedClaims(token).getPayload();
                    // Keycloak tokens: grant ROLE_OPERATOR for authenticated users
                    List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_OPERATOR"),
                        new SimpleGrantedAuthority("ROLE_ANALYTICS_READ")
                    );
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                    // Store tenant_id for cross-tenant enforcement in controller
                    String tenantId = claims.get("tenant_id", String.class);
                    if (tenantId != null) {
                        auth.setDetails(java.util.Map.of("tenant_id", tenantId));
                    }
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    return true;
                } catch (Exception e) {
                    log.trace("RSA JWT validation failed: {}", e.getMessage());
                    return false;
                }
            }
        };
    }
}

