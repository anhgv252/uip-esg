package com.uip.backend.scheduler;

import com.uip.backend.environment.service.EnvironmentService;
import com.uip.backend.notification.service.SseEmitterRegistry;
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
 * Placed in top-level scheduler package (not inside any module) to avoid
 * violating notification ↔ environment module boundary rules.
 *
 * Fires every 30 seconds. Only runs when at least one SSE client is connected
 * (activeCount > 0) to avoid unnecessary DB/computation overhead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnvironmentBroadcastScheduler {

    private final EnvironmentService  environmentService;
    private final SseEmitterRegistry  sseEmitterRegistry;

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void broadcastSensorUpdates() {
        if (sseEmitterRegistry.activeCount() == 0) {
            return; // Skip when no clients connected
        }

        try {
            var readings = environmentService.getCurrentAqi();
            for (var reading : readings) {
                // Build SENSOR_UPDATE payload matching the shape the frontend expects:
                // { type: "SENSOR_UPDATE", sensor: { id, aqiValue, category, districtCode, … } }
                var payload = Map.of(
                        "type", "SENSOR_UPDATE",
                        "sensor", Map.of(
                                "id",           reading.getSensorId(),
                                "aqiValue",     reading.getAqiValue() != null ? reading.getAqiValue() : 0,
                                "category",     reading.getCategory() != null ? reading.getCategory() : "UNKNOWN",
                                "district",     reading.getDistrictCode() != null ? reading.getDistrictCode() : "",
                                "lastSeenAt",   reading.getTimestamp() != null ? reading.getTimestamp().toString() : "",
                                "status",       "ONLINE"
                        )
                );
                sseEmitterRegistry.broadcast("message", payload);
            }
            log.debug("Broadcast {} SENSOR_UPDATE events to {} SSE clients",
                    readings.size(), sseEmitterRegistry.activeCount());
        } catch (Exception e) {
            log.warn("Failed to broadcast sensor updates: {}", e.getMessage());
        }
    }
}
