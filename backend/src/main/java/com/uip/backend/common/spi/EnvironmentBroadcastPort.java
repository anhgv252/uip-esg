package com.uip.backend.common.spi;

import java.time.Instant;
import java.util.List;

/**
 * Port for reading current air-quality snapshot data that needs to be broadcast to clients.
 *
 * <p>Consumed by the top-level {@code scheduler} module (e.g. {@code EnvironmentBroadcastScheduler})
 * which must not depend on {@code environment.service} directly (ADR-052, migration D3).
 * The {@code environment} module provides the implementation.</p>
 *
 * <p>Returns a neutral {@link AqiSnapshot} projection so consumers never import {@code environment}
 * DTOs — keeping the modular-monolith boundary intact.</p>
 */
public interface EnvironmentBroadcastPort {

    /**
     * Current AQI readings for all live sensors, projected as broadcast-neutral snapshots.
     */
    List<AqiSnapshot> getCurrentAqiSnapshots();

    /**
     * Neutral projection of a single sensor's current AQI reading for SSE broadcast.
     * Mirrors the payload shape the frontend expects (SENSOR_UPDATE event).
     */
    record AqiSnapshot(
            String sensorId,
            Integer aqiValue,
            String category,
            String districtCode,
            Instant timestamp
    ) {}
}
