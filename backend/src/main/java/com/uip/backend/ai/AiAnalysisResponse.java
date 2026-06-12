package com.uip.backend.ai;

import java.io.Serializable;

/**
 * M4-AI-04: Immutable response from the AI inference layer for AQI analysis.
 *
 * <p>Stored as the cached value in Redis DB 2 (cache: {@code ai-responses}).
 * Must implement {@link Serializable} for Jackson/Redis serialisation.</p>
 */
public record AiAnalysisResponse(
        String districtCode,
        String aqiRange,
        String recommendation,
        String modelUsed,
        long timestampMs
) implements Serializable {

    /**
     * Returns a sentinel response used when the Claude API key is not configured
     * or when circuit-breaker is open. Cached like a normal response so fallback
     * text is also deduplicated.
     */
    public static AiAnalysisResponse fallback(String districtCode, String aqiRange) {
        return new AiAnalysisResponse(
                districtCode,
                aqiRange,
                "AI analysis unavailable — operating under fallback mode.",
                "none",
                System.currentTimeMillis()
        );
    }
}
