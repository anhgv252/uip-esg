package com.uip.backend.forecast;

import java.time.Instant;

/**
 * Single data point trong forecast result.
 *
 * @param timestamp       hour timestamp (UTC epoch)
 * @param actualValue     observed value (null cho future points)
 * @param predictedValue  model prediction
 * @param confidenceUpper 95% CI upper bound
 * @param confidenceLower 95% CI lower bound
 * @param isAnomaly       true if actual outside CI
 */
public record ForecastPoint(
        Instant timestamp,
        Double actualValue,
        Double predictedValue,
        Double confidenceUpper,
        Double confidenceLower,
        boolean isAnomaly
) {}
