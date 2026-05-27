package com.uip.backend.forecast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Forecast orchestrator — delegates to active ForecastPort implementation.
 * Cache only successful domain responses, not ResponseEntity wrappers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final ForecastPort forecastPort;

    @Cacheable(value = "forecasts", key = "#tenantId + '|' + #buildingId + '|' + #horizonDays")
    public ForecastResult forecast(String tenantId, String buildingId, int horizonDays) {
        log.info("Forecast request: tenant={}, building={}, horizon={}d", tenantId, buildingId, horizonDays);
        return forecastPort.forecast(tenantId, buildingId, horizonDays);
    }
}
