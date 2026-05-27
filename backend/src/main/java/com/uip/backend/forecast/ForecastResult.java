package com.uip.backend.forecast;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Forecast result DTO — trả về từ ForecastPort implementations.
 *
 * @param tenantId     tenant context
 * @param buildingId   building context
 * @param model        model name ("ARIMA", "NAIVE", etc.)
 * @param isFallback   true if naive fallback was used
 * @param mape         MAPE score (null if not computed)
 * @param points       forecast data points
 * @param generatedAt  when this forecast was generated
 */
public record ForecastResult(
        String tenantId,
        String buildingId,
        String model,
        boolean isFallback,
        Double mape,
        List<ForecastPoint> points,
        Instant generatedAt
) {
    public static ForecastResult insufficientData(String tenantId, String buildingId) {
        return new ForecastResult(
                tenantId, buildingId, "NONE", true, null,
                Collections.emptyList(), Instant.now()
        );
    }
}
