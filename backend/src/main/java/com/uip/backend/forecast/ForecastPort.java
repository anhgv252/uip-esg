package com.uip.backend.forecast;

/**
 * Port interface cho forecast computation (ADR-032).
 *
 * "python" = ForecastServiceAdapter → Python FastAPI forecast-service
 * "naive"  = NaiveForecastAdapter → in-process rolling average (TimescaleDB)
 * "disabled" = no bean → ForecastController returns 501
 */
public interface ForecastPort {

    ForecastResult forecast(String tenantId, String buildingId, int horizonDays);
}
