package com.uip.backend.scheduler;

import com.uip.backend.common.spi.EnvironmentBroadcastPort;
import com.uip.backend.common.spi.SseBroadcastPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Periodically broadcasts current sensor AQI readings to all connected SSE clients
 * as SENSOR_UPDATE events. This is consumed by CityOpsPage / useMapSSE to keep
 * sensor marker colours up-to-date without requiring a page reload.
 *
 * <p>Placed in top-level scheduler package (not inside any module) to avoid violating
 * notification ↔ environment module boundary rules. Depends only on neutral Ports
 * ({@link EnvironmentBroadcastPort}, {@link SseBroadcastPort}) per ADR-052 — never on
 * {@code environment.service} or {@code notification.service} directly (migration D3).</p>
 *
 * Fires every 30 seconds. Only runs when at least one SSE client is connected
 * (activeCount > 0) to avoid unnecessary DB/computation overhead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnvironmentBroadcastScheduler {

    private final EnvironmentBroadcastPort environmentBroadcastPort;
    private final SseBroadcastPort         sseBroadcastPort;

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void broadcastSensorUpdates() {
        if (sseBroadcastPort.activeCount() == 0) {
            return; // Skip when no clients connected
        }

        try {
            var readings = environmentBroadcastPort.getCurrentAqiSnapshots();
            for (var reading : readings) {
                // Build SENSOR_UPDATE payload matching the shape the frontend expects:
                // { type: "SENSOR_UPDATE", sensor: { id, aqiValue, category, districtCode, … } }
                var payload = Map.of(
                        "type", "SENSOR_UPDATE",
                        "sensor", Map.of(
                                "id",           reading.sensorId(),
                                "aqiValue",     reading.aqiValue() != null ? reading.aqiValue() : 0,
                                "category",     reading.category() != null ? reading.category() : "UNKNOWN",
                                "district",     reading.districtCode() != null ? reading.districtCode() : "",
                                "lastSeenAt",   reading.timestamp() != null ? reading.timestamp().toString() : "",
                                "status",       "ONLINE"
                        )
                );
                sseBroadcastPort.broadcast("message", payload);
            }
            log.debug("Broadcast {} SENSOR_UPDATE events to {} SSE clients",
                    readings.size(), sseBroadcastPort.activeCount());
        } catch (Exception e) {
            log.warn("Failed to broadcast sensor updates: {}", e.getMessage());
        }
    }
}
