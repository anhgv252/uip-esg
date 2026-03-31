package com.uip.backend.auth.service;

import com.uip.backend.auth.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
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

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    // 32-byte key, Base64-encoded for HS256
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

    @Test
    @DisplayName("Access token: extracted username matches")
    void accessToken_extractedUsername_matches() {
        String token = tokenProvider.generateAccessToken(userDetails);
        assertThat(tokenProvider.extractUsername(token)).isEqualTo("operator");
    }

    @Test
    @DisplayName("Access token is valid for correct user")
    void accessToken_isValid_forCorrectUser() {
        String token = tokenProvider.generateAccessToken(userDetails);
        assertThat(tokenProvider.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("Refresh token is valid as refresh type")
    void refreshToken_isValidRefresh() {
        String token = tokenProvider.generateRefreshToken(userDetails);
        assertThat(tokenProvider.isRefreshTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("Refresh token is NOT valid as regular access token")
    void refreshToken_isNotValidAsAccessToken() {
        String token = tokenProvider.generateRefreshToken(userDetails);
        assertThat(tokenProvider.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    @DisplayName("Access token is NOT valid as refresh token")
    void accessToken_isNotValidAsRefreshToken() {
        String token = tokenProvider.generateAccessToken(userDetails);
        assertThat(tokenProvider.isRefreshTokenValid(token, userDetails)).isFalse();
    }

    @Test
    @DisplayName("Expired token returns false for isTokenValid")
    void expiredToken_isNotValid() {
        when(jwtProperties.getExpirationMs()).thenReturn(1L); // 1ms expiry
        // need to reinitialise provider after mock change — generate then wait
        String token = tokenProvider.generateAccessToken(userDetails);
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertThat(tokenProvider.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    @DisplayName("Token for different user is invalid")
    void tokenForDifferentUser_isInvalid() {
        String token = tokenProvider.generateAccessToken(userDetails);
        UserDetails otherUser = new User("other", "pw", List.of());
        assertThat(tokenProvider.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    @DisplayName("Tampered token is invalid")
    void tamperedToken_isInvalid() {
        String token = tokenProvider.generateAccessToken(userDetails);
        String tampered = token + "x";
        assertThat(tokenProvider.isTokenValid(tampered, userDetails)).isFalse();
    }
}
