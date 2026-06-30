package com.uip.backend.common.spi;

import java.time.Instant;

/**
 * Port for air-quality (PM2.5) aggregate queries, consumed by bounded contexts that
 * need environmental data (e.g. {@code esg} for LOTUS VN IEQ + ISO 37120 scoring).
 *
 * <p>Lives in the neutral {@code common.spi} package so that the {@code esg} module can
 * depend on this interface without accessing {@code environment.repository} directly,
 * satisfying the modular-monolith boundary rules in {@code ModuleBoundaryArchTest}
 * (BUG-M5-009). The {@code environment} module provides the implementation
 * ({@code AirQualityAdapter}).</p>
 *
 * <p>Hexagonal port: business code depends only on this interface — swap implementation
 * (TimescaleDB, ClickHouse, REST) without touching consumers.</p>
 */
public interface AirQualityPort {

    /**
     * Average PM2.5 (µg/m³) for sensors whose sensorId starts with the given buildingId
     * prefix, within the specified time range. Returns {@code null} when no readings exist.
     */
    Double findAveragePm25ByBuildingAndPeriod(String buildingId, Instant from, Instant to);

    /**
     * Average PM2.5 (µg/m³) for all sensors of a tenant over a time period.
     * Returns {@code null} when no readings exist.
     */
    Double findAveragePm25ByPeriod(String tenantId, Instant start, Instant end);
}
