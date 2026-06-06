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
        // Backend simulate endpoint sends measurements as {"value": <number>} for single-metric sensors.
        // Map the generic "value" key to the appropriate metric column based on sensorType.
        String sensorType = (msg.getSensorType() != null && msg.getSensorType().getValue() != null)
                ? msg.getSensorType().getValue().toLowerCase() : "";
        Double genericValue = measurements.getOrDefault("value", null);

        Double aqi = measurements.containsKey("aqi") ? measurements.get("aqi")
                : (sensorType.contains("aqi") || sensorType.contains("air")) ? genericValue : null;
        Double temperature = measurements.containsKey("temperature") ? measurements.get("temperature")
                : sensorType.contains("temp") ? genericValue : null;
        Double humidity = measurements.containsKey("humidity") ? measurements.get("humidity")
                : sensorType.contains("humid") ? genericValue : null;

        return new EnvironmentReading(
                msg.getDeviceIdValue(),
                Instant.ofEpochMilli(msg.getObservedAtMillis()),
                aqi,
                measurements.getOrDefault("pm25", null),
                measurements.getOrDefault("pm10", null),
                measurements.getOrDefault("o3", null),
                measurements.getOrDefault("no2", null),
                measurements.getOrDefault("so2", null),
                measurements.getOrDefault("co", null),
                temperature,
                humidity,
                rawJson
        );
    }
}
