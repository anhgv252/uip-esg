package com.uip.backend.forecast;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op forecast adapter when forecast-engine=disabled.
 * Returns 501 NOT IMPLEMENTED via ForecastService.
 */
@Component
@ConditionalOnProperty(name = "uip.capabilities.forecast-engine", havingValue = "disabled")
public class DisabledForecastAdapter implements ForecastPort {

    @Override
    public ForecastResult forecast(String tenantId, String buildingId, int horizonDays) {
        throw new ForecastServiceUnavailableException(
                "Forecast engine is disabled (uip.capabilities.forecast-engine=disabled)", null);
    }
}
