package com.uip.backend.auth.api.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn   // seconds until access token expiration
) {}
