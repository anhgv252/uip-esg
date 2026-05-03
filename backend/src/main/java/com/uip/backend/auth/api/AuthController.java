package com.uip.backend.auth.api;

import com.uip.backend.auth.api.dto.AuthResponse;
import com.uip.backend.auth.api.dto.LoginRequest;
import com.uip.backend.auth.api.dto.RefreshRequest;
import com.uip.backend.auth.service.AuthService;
import com.uip.backend.auth.service.LoginRateLimitService;
import com.uip.backend.auth.service.JwtTokenProvider;
import com.uip.backend.auth.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT auth endpoints")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimitService rateLimitService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        if (!rateLimitService.tryConsume(httpRequest.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many login attempts. Please try again in 1 minute.");
        }

        AuthResponse authResponse = authService.login(request);

        Cookie accessTokenCookie = new Cookie("access_token", authResponse.accessToken());
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(false); // true in production (HTTPS)
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge((int) authResponse.expiresIn());
        response.addCookie(accessTokenCookie);

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.refresh(request.refreshToken());

        Cookie accessTokenCookie = new Cookie("access_token", authResponse.accessToken());
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(false); // true in production (HTTPS)
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge((int) authResponse.expiresIn());
        response.addCookie(accessTokenCookie);

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — clear httpOnly cookie and invalidate token")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Invalidate the current token by adding it to the blacklist
        String jwt = extractToken(request);
        if (jwt != null) {
            try {
                long expiresAt = jwtTokenProvider.extractExpirationMs(jwt);
                tokenBlacklistService.invalidate(jwt, expiresAt);
            } catch (Exception ignored) {
                // token may already be expired or malformed — clear cookie regardless
            }
        }

        Cookie expiredCookie = new Cookie("access_token", "");
        expiredCookie.setHttpOnly(true);
        expiredCookie.setSecure(false);
        expiredCookie.setPath("/");
        expiredCookie.setMaxAge(0);
        response.addCookie(expiredCookie);
        return ResponseEntity.ok().build();
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
