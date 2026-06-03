package com.uip.backend.forecast;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Forecast orchestrator — delegates to active ForecastPort implementation.
 * Falls back to NaiveForecastAdapter when primary port throws
 * ForecastServiceUnavailableException (BUG-S4-T04).
 */
@Slf4j
@Service
public class ForecastService {

    private final ForecastPort forecastPort;
    private final NaiveForecastAdapter naiveFallback;

    @Autowired
    public ForecastService(ForecastPort forecastPort,
                           @Qualifier("naiveForecastFallback") NaiveForecastAdapter naiveFallback) {
        this.forecastPort = forecastPort;
        this.naiveFallback = naiveFallback;
    }

    // unless = isFallback: NONE/naive results are never cached so the next call retries the real service.
    // Eliminates manual Redis DEL for stale NONE entries (B1-7).
    @Cacheable(
            value = "forecasts",
            key = "#tenantId + '|' + #buildingId + '|' + #horizonDays",
            unless = "#result.isFallback()"
    )
    public ForecastResult forecast(String tenantId, String buildingId, int horizonDays) {
        log.info("Forecast request: tenant={}, building={}, horizon={}d", tenantId, buildingId, horizonDays);
        try {
            return forecastPort.forecast(tenantId, buildingId, horizonDays);
        } catch (ForecastServiceUnavailableException e) {
            log.warn("Primary forecast unavailable, falling back to naive: {}", e.getMessage());
            return naiveFallback.forecast(tenantId, buildingId, horizonDays);
        }
    }
}
