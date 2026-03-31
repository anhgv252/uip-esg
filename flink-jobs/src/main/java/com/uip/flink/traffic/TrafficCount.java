package com.uip.flink.traffic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Mapped POJO for traffic.traffic_counts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrafficCount implements Serializable {
    private String intersectionId;
    private Instant timestamp;
    private Integer vehicleCount;
    private Double avgSpeedKmh;
    private String congestionLevel;
    private String rawPayload;

    public static TrafficCount from(com.uip.flink.common.NgsiLdMessage msg, String rawJson) {
        var measurements = msg.getMeasurementValues();
        Double count = measurements.getOrDefault("vehicle_count", null);
        Double speed = measurements.getOrDefault("avg_speed_kmh", null);
        String congestion = deriveCongestionLevel(count, speed);
        return new TrafficCount(
                msg.getDeviceIdValue(),
                Instant.ofEpochMilli(msg.getObservedAtMillis()),
                count != null ? count.intValue() : 0,
                speed,
                congestion,
                rawJson
        );
    }

    private static String deriveCongestionLevel(Double count, Double speed) {
        if (count == null) return "UNKNOWN";
        if (count > 300 || (speed != null && speed < 10)) return "CRITICAL";
        if (count > 200 || (speed != null && speed < 20)) return "HIGH";
        if (count > 100 || (speed != null && speed < 40)) return "MEDIUM";
        return "LOW";
    }
}
