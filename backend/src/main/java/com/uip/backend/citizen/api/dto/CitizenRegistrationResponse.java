package com.uip.backend.citizen.api.dto;

/**
 * Returned from POST /api/v1/citizen/register.
 * Includes the new citizen profile AND a JWT so the client can immediately
 * proceed to Step 2 (household linking) without a separate login round-trip.
 */
public record CitizenRegistrationResponse(
        CitizenProfileDto profile,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {}
