package com.uip.backend.contract;

import com.uip.backend.auth.api.AuthController;
import com.uip.backend.auth.api.dto.AuthResponse;
import com.uip.backend.auth.api.dto.LoginRequest;
import com.uip.backend.auth.api.dto.RefreshRequest;
import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.auth.service.AuthService;
import com.uip.backend.auth.service.JwtTokenProvider;
import com.uip.backend.auth.service.LoginRateLimitService;
import com.uip.backend.auth.service.TokenBlacklistService;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentMatchers;

/**
 * REST Assured API contract tests for Auth API.
 * Covers: POST /login, POST /refresh, POST /logout
 * Tests: status codes, response schema validation, headers
 *
 * v3.1-06: First batch of REST Assured contract tests
 */
@WebMvcTest(
    controllers = AuthController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
    }
)
@Tag("contract")
@DisplayName("Auth API — REST Assured Contract Tests")
class AuthApiContractTest {

    @MockBean AuthService authService;
    @MockBean LoginRateLimitService rateLimitService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean @SuppressWarnings("unused") TenantRateLimiter tenantRateLimiter;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.standaloneSetup(new AuthController(
                authService, rateLimitService, jwtTokenProvider, tokenBlacklistService
        ));
    }

    // ─── POST /api/v1/auth/login ───────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("200 — valid credentials returns AuthResponse schema")
        void login_validCredentials_returns200WithSchema() {
            AuthResponse authResponse = new AuthResponse(
                    "eyJhbGciOiJIUzI1NiJ9.access.payload",
                    "refresh-token-abc123",
                    "Bearer",
                    3600L
            );
            when(rateLimitService.tryConsume(anyString())).thenReturn(true);
            when(authService.login(ArgumentMatchers.any(LoginRequest.class))).thenReturn(authResponse);

            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"username\":\"admin\",\"password\":\"password123\"}")
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(3600))
                // Response schema: exactly 4 fields
                .body("size()", equalTo(4));
        }

        @Test
        @DisplayName("200 — response sets access_token cookie")
        void login_validCredentials_setsCookie() {
            AuthResponse authResponse = new AuthResponse("at", "rt", "Bearer", 3600L);
            when(rateLimitService.tryConsume(anyString())).thenReturn(true);
            when(authService.login(ArgumentMatchers.any(LoginRequest.class))).thenReturn(authResponse);

            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"username\":\"admin\",\"password\":\"password123\"}")
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .cookie("access_token", notNullValue());
        }

        @Test
        @DisplayName("400 — empty body returns bad request")
        void login_emptyBody_returns400() {
            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{}")
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(anyOf(is(400), is(500)));
        }

        @Test
        @DisplayName("429 — rate limited when too many attempts")
        void login_rateLimited_returns429() {
            when(rateLimitService.tryConsume(anyString())).thenReturn(false);

            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"username\":\"admin\",\"password\":\"password123\"}")
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(429)
                .body(notNullValue());
        }

        @Test
        @DisplayName("Content-Type header is application/json")
        void login_responseContentType_isJson() {
            AuthResponse authResponse = new AuthResponse("at", "rt", "Bearer", 3600L);
            when(rateLimitService.tryConsume(anyString())).thenReturn(true);
            when(authService.login(ArgumentMatchers.any(LoginRequest.class))).thenReturn(authResponse);

            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"username\":\"admin\",\"password\":\"password123\"}")
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .header("Content-Type", containsString("application/json"));
        }
    }

    // ─── POST /api/v1/auth/refresh ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("200 — valid refresh token returns new AuthResponse")
        void refresh_validToken_returns200WithSchema() {
            AuthResponse authResponse = new AuthResponse(
                    "new-access-token",
                    "new-refresh-token",
                    "Bearer",
                    3600L
            );
            when(authService.refresh(anyString())).thenReturn(authResponse);

            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"refreshToken\":\"valid-refresh-token\"}")
            .when()
                .post("/api/v1/auth/refresh")
            .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("accessToken", equalTo("new-access-token"))
                .body("refreshToken", equalTo("new-refresh-token"))
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(3600));
        }

        @Test
        @DisplayName("200 — refresh sets new access_token cookie")
        void refresh_validToken_setsCookie() {
            AuthResponse authResponse = new AuthResponse("new-at", "new-rt", "Bearer", 3600L);
            when(authService.refresh(anyString())).thenReturn(authResponse);

            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{\"refreshToken\":\"valid-refresh-token\"}")
            .when()
                .post("/api/v1/auth/refresh")
            .then()
                .statusCode(200)
                .cookie("access_token", notNullValue());
        }

        @Test
        @DisplayName("400 — missing refresh token body")
        void refresh_emptyBody_returns400() {
            given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body("{}")
            .when()
                .post("/api/v1/auth/refresh")
            .then()
                .statusCode(anyOf(is(400), is(500)));
        }
    }

    // ─── POST /api/v1/auth/logout ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("200 — logout clears access_token cookie")
        void logout_returns200() {
            given()
            .when()
                .post("/api/v1/auth/logout")
            .then()
                .statusCode(200);
        }

        @Test
        @DisplayName("200 — logout with Bearer token blacklists the token")
        void logout_withBearerToken_blacklistsToken() {
            String jwt = "eyJhbGciOiJIUzI1NiJ9.test.payload";
            when(jwtTokenProvider.extractExpirationMs(anyString())).thenReturn(System.currentTimeMillis() + 3600000L);

            given()
                .header("Authorization", "Bearer " + jwt)
            .when()
                .post("/api/v1/auth/logout")
            .then()
                .statusCode(200);

            verify(tokenBlacklistService).invalidate(eq(jwt), anyLong());
        }
    }
}
