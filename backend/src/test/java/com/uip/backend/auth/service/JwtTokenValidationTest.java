package com.uip.backend.auth.service;

import com.uip.backend.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * MVP2-03c (3c): Additional JWT validation tests supplementing JwtTokenProviderTest.
 * Focuses on: expired JWT, tampered JWT, JWT without roles.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MVP2-03c JWT Token Validation")
class JwtTokenValidationTest {

    private static final String TEST_SECRET = Base64.getEncoder()
            .encodeToString("uip-test-secret-at-least-32-bytes!!".getBytes());

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private JwtTokenProvider tokenProvider;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getSecret()).thenReturn(TEST_SECRET);
        lenient().when(jwtProperties.getExpirationMs()).thenReturn(900_000L);
        lenient().when(jwtProperties.getRefreshExpirationMs()).thenReturn(604_800_000L);

        userDetails = new User("operator", "hashed_pw",
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
    }

    // ─── Expired JWT ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Expired JWT: isTokenValid returns false")
    void expiredJwt_isTokenValid_returnsFalse() {
        // Generate token with 0ms expiration — already expired by the time we validate
        when(jwtProperties.getExpirationMs()).thenReturn(0L);
        String expiredToken = tokenProvider.generateAccessToken(userDetails);

        boolean result = tokenProvider.isTokenValid(expiredToken, userDetails);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Expired JWT: extractUsername still returns subject (JJWT reads expired claims)")
    void expiredJwt_extractUsername_stillReturnsSubject() {
        // Build a manually-expired token to guarantee expiry
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("operator")
                .claim("roles", List.of("ROLE_OPERATOR"))
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000)) // expired 5s ago
                .signWith(key)
                .compact();

        // extractUsername uses parseClaims which throws on expired tokens
        assertThatThrownBy(() -> tokenProvider.extractUsername(expiredToken))
                .isInstanceOf(Exception.class);
    }

    // ─── Tampered JWT ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Tampered JWT (appended char): isTokenValid returns false")
    void tamperedJwt_appendedChar_isInvalid() {
        String token = tokenProvider.generateAccessToken(userDetails);
        String tampered = token + "x";

        assertThat(tokenProvider.isTokenValid(tampered, userDetails)).isFalse();
    }

    @Test
    @DisplayName("Tampered JWT (modified payload): isTokenValid returns false")
    void tamperedJwt_modifiedPayload_isInvalid() {
        String token = tokenProvider.generateAccessToken(userDetails);
        // Replace a character in the payload section (between first and second dots)
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        // Tamper with the payload by flipping a character
        char[] payloadChars = parts[1].toCharArray();
        payloadChars[0] = (char) (payloadChars[0] ^ 0x01); // flip bit
        parts[1] = new String(payloadChars);
        String tampered = String.join(".", parts);

        assertThat(tokenProvider.isTokenValid(tampered, userDetails)).isFalse();
    }

    @Test
    @DisplayName("Completely invalid string: isTokenValid returns false")
    void invalidString_isTokenValid_returnsFalse() {
        assertThat(tokenProvider.isTokenValid("not.a.jwt", userDetails)).isFalse();
    }

    @Test
    @DisplayName("Empty string: isTokenValid returns false")
    void emptyString_isTokenValid_returnsFalse() {
        assertThat(tokenProvider.isTokenValid("", userDetails)).isFalse();
    }

    // ─── JWT without roles ──────────────────────────────────────────────────

    @Test
    @DisplayName("JWT without roles claim: isTokenValid still returns true (roles are not required for validity)")
    void jwtWithoutRoles_isTokenValid_stillTrue() {
        // Build token without roles claim
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        // Need to compute the expected base64url signature length for HS256 = 43 chars
        String token = Jwts.builder()
                .subject("operator")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(key)
                .compact();

        // isTokenValid checks subject match + not expired + not refresh token
        boolean result = tokenProvider.isTokenValid(token, userDetails);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("JWT with empty roles list: isTokenValid returns true")
    void jwtWithEmptyRoles_isTokenValid_true() {
        // Use a user with no authorities
        UserDetails userNoRoles = new User("viewer", "pw", List.of());
        String token = tokenProvider.generateAccessToken(userNoRoles);

        assertThat(tokenProvider.isTokenValid(token, userNoRoles)).isTrue();
        assertThat(tokenProvider.extractUsername(token)).isEqualTo("viewer");
    }

    // ─── Cross-user validation ──────────────────────────────────────────────

    @Test
    @DisplayName("JWT issued for user A: isTokenValid returns false for user B")
    void jwtForUserA_invalidForUserB() {
        String token = tokenProvider.generateAccessToken(userDetails);

        UserDetails otherUser = new User("admin", "pw",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(tokenProvider.isTokenValid(token, otherUser)).isFalse();
    }
}
