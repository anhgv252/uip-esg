package com.uip.backend.auth.config;

import com.uip.backend.auth.service.JwtTokenProvider;
import com.uip.backend.auth.service.TokenBlacklistService;
import com.uip.backend.auth.service.UipUserDetailsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * v3.1-09: JWT validation IT — tests JwtAuthenticationFilter with real JwtTokenProvider.
 * Covers: expired token, tampered token, missing claims, valid token, blacklisted token.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("v3.1-09 JwtAuthenticationFilter IT")
class JwtAuthenticationFilterIT {

    private static final String TEST_SECRET = Base64.getEncoder()
            .encodeToString("uip-test-secret-at-least-32-bytes!!".getBytes());

    @Mock private UipUserDetailsService userDetailsService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private JwtTokenProvider tokenProvider;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        JwtProperties props = new JwtProperties();
        ReflectionTestUtils.setField(props, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(props, "expirationMs", 900_000L);
        ReflectionTestUtils.setField(props, "refreshExpirationMs", 604_800_000L);
        ReflectionTestUtils.setField(props, "hmacIssuer", "uip-legacy");

        tokenProvider = new JwtTokenProvider(props);
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService, tokenBlacklistService);

        userDetails = new User("testoperator", "hashed_pw",
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
    }

    // ─── Valid token ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid JWT → sets SecurityContext authentication")
    void validToken_setsAuthentication() throws Exception {
        String token = tokenProvider.generateAccessToken(userDetails);
        when(userDetailsService.loadUserByUsername("testoperator")).thenReturn(userDetails);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("testoperator");
        verify(filterChain).doFilter(req, res);
    }

    @Test
    @DisplayName("Valid JWT via cookie → sets SecurityContext authentication")
    void validTokenViaCookie_setsAuthentication() throws Exception {
        String token = tokenProvider.generateAccessToken(userDetails);
        when(userDetailsService.loadUserByUsername("testoperator")).thenReturn(userDetails);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new jakarta.servlet.http.Cookie("access_token", token));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("testoperator");
    }

    // ─── Expired token ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Expired JWT → no authentication set, chain continues")
    void expiredToken_noAuthentication() throws Exception {
        JwtProperties shortProps = new JwtProperties();
        ReflectionTestUtils.setField(shortProps, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(shortProps, "expirationMs", 1L);
        ReflectionTestUtils.setField(shortProps, "refreshExpirationMs", 604_800_000L);
        ReflectionTestUtils.setField(shortProps, "hmacIssuer", "uip-legacy");
        JwtTokenProvider shortProvider = new JwtTokenProvider(shortProps);

        String expiredToken = shortProvider.generateAccessToken(userDetails);
        Thread.sleep(50); // ensure token expires

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + expiredToken);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(req, res);
    }

    // ─── Tampered token ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Tampered JWT (appended char) → no authentication set")
    void tamperedToken_appendedChar_noAuthentication() throws Exception {
        String token = tokenProvider.generateAccessToken(userDetails);
        String tampered = token + "x";

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + tampered);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(req, res);
    }

    @Test
    @DisplayName("Tampered JWT (modified payload) → no authentication set")
    void tamperedToken_modifiedPayload_noAuthentication() throws Exception {
        String token = tokenProvider.generateAccessToken(userDetails);
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        char[] payloadChars = parts[1].toCharArray();
        payloadChars[0] = (char) (payloadChars[0] ^ 0x01);
        parts[1] = new String(payloadChars);
        String tampered = String.join(".", parts);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + tampered);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(req, res);
    }

    // ─── Missing token / no Authorization header ────────────────────────────

    @Test
    @DisplayName("No Authorization header → no authentication, chain continues")
    void noAuthHeader_noAuthentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(req, res);
    }

    @Test
    @DisplayName("Malformed Authorization header (no Bearer prefix) → no authentication")
    void malformedAuthHeader_noAuthentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(req, res);
    }

    // ─── Blacklisted token ───────────────────────────────────────────────────

    @Test
    @DisplayName("Blacklisted JWT → no authentication set")
    void blacklistedToken_noAuthentication() throws Exception {
        String token = tokenProvider.generateAccessToken(userDetails);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(req, res);
    }

    // ─── Token with scopes ───────────────────────────────────────────────────

    @Test
    @DisplayName("Multi-tenant JWT with scopes → scopes added to authorities")
    void multiTenantToken_scopesInAuthorities() throws Exception {
        String token = tokenProvider.generateAccessToken(userDetails, "hcm",
                List.of("esg:read", "esg:write"), List.of("BLD-01"));
        when(userDetailsService.loadUserByUsername("testoperator")).thenReturn(userDetails);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertThat(authorities.stream().map(Object::toString))
                .contains("esg:read", "esg:write", "ROLE_OPERATOR");
    }
}
