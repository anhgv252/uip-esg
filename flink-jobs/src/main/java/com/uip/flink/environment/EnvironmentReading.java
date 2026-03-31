package com.uip.flink.environment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Mapped POJO for environment.sensor_readings
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentReading implements Serializable {

    private String sensorId;
    private Instant timestamp;
    private Double aqi;
    private Double pm25;
    private Double pm10;
    private Double o3;
    private Double no2;
    private Double so2;
    private Double co;
    private Double temperature;
    private Double humidity;
    private String rawPayload;   // original JSON for audit

    public static EnvironmentReading from(com.uip.flink.common.NgsiLdMessage msg, String rawJson) {
        var measurements = msg.getMeasurementValues();
        return new EnvironmentReading(
                msg.getDeviceIdValue(),
                Instant.ofEpochMilli(msg.getObservedAtMillis()),
                measurements.getOrDefault("aqi", null),
                measurements.getOrDefault("pm25", null),
                measurements.getOrDefault("pm10", null),
                measurements.getOrDefault("o3", null),
                measurements.getOrDefault("no2", null),
                measurements.getOrDefault("so2", null),
                measurements.getOrDefault("co", null),
                measurements.getOrDefault("temperature", null),
                measurements.getOrDefault("humidity", null),
                rawJson
        );
    }
}
