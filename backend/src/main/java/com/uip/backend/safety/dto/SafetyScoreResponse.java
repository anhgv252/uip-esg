package com.uip.backend.safety.dto;

import com.uip.backend.safety.model.SafetyScore;

import java.time.Instant;

public record SafetyScoreResponse(
        int score,
        String status,       // SAFE | WARNING | CRITICAL | OFFLINE
        Instant lastUpdated,
        int activeAlerts
) {
    public static SafetyScoreResponse from(SafetyScore s) {
        return new SafetyScoreResponse(s.score(), s.status(), s.lastUpdated(), s.activeAlerts());
    }
}
