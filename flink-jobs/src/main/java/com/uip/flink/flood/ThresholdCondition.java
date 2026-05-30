package com.uip.flink.flood;

import java.util.Map;
import java.util.Objects;

/**
 * Threshold definitions for flood sensor types per TCVN 9386:2012.
 *
 * Three severity levels per sensor type:
 *   P2_ADVISORY  — monitor, advisory only
 *   P1_WARNING   — escalate to operations team
 *   P0_EMERGENCY — broadcast emergency alert
 *
 * Used by FloodAlertJob to classify readings and determine alert severity.
 */
public final class ThresholdCondition {

    // --- Rainfall thresholds (mm/h) ---
    public static final double RAINFALL_P2 = 50.0;
    public static final double RAINFALL_P1 = 80.0;
    public static final double RAINFALL_P0 = 120.0;

    // --- Water level thresholds (m) ---
    public static final double WATER_LEVEL_P2 = 2.0;
    public static final double WATER_LEVEL_P1 = 3.5;
    public static final double WATER_LEVEL_P0 = 5.0;

    // --- Soil moisture thresholds (%) ---
    public static final double SOIL_MOISTURE_P2 = 70.0;
    public static final double SOIL_MOISTURE_P1 = 85.0;
    public static final double SOIL_MOISTURE_P0 = 95.0;

    /** Flood-relevant sensor types */
    public static final String RAINFALL = "RAINFALL";
    public static final String WATER_LEVEL = "WATER_LEVEL";
    public static final String SOIL_MOISTURE = "SOIL_MOISTURE";

    private ThresholdCondition() { /* utility class */ }

    /**
     * Check if the sensor type is flood-relevant.
     */
    public static boolean isFloodSensor(String sensorType) {
        return RAINFALL.equalsIgnoreCase(sensorType)
                || WATER_LEVEL.equalsIgnoreCase(sensorType)
                || SOIL_MOISTURE.equalsIgnoreCase(sensorType);
    }

    /**
     * Get the P2 (advisory) threshold for a sensor type.
     * This is the minimum threshold for any flood alert.
     * Returns -1 if the sensor type is not flood-relevant.
     */
    public static double getP2Threshold(String sensorType) {
        if (RAINFALL.equalsIgnoreCase(sensorType)) return RAINFALL_P2;
        if (WATER_LEVEL.equalsIgnoreCase(sensorType)) return WATER_LEVEL_P2;
        if (SOIL_MOISTURE.equalsIgnoreCase(sensorType)) return SOIL_MOISTURE_P2;
        return -1;
    }

    /**
     * Classify a reading into severity level based on sensor type and value.
     *
     * @return severity string: "P0_EMERGENCY", "P1_WARNING", "P2_ADVISORY", or null if below threshold
     */
    public static String classifySeverity(String sensorType, double value) {
        Objects.requireNonNull(sensorType, "sensorType must not be null");

        if (RAINFALL.equalsIgnoreCase(sensorType)) {
            if (value >= RAINFALL_P0) return "P0_EMERGENCY";
            if (value >= RAINFALL_P1) return "P1_WARNING";
            if (value >= RAINFALL_P2) return "P2_ADVISORY";
        } else if (WATER_LEVEL.equalsIgnoreCase(sensorType)) {
            if (value >= WATER_LEVEL_P0) return "P0_EMERGENCY";
            if (value >= WATER_LEVEL_P1) return "P1_WARNING";
            if (value >= WATER_LEVEL_P2) return "P2_ADVISORY";
        } else if (SOIL_MOISTURE.equalsIgnoreCase(sensorType)) {
            if (value >= SOIL_MOISTURE_P0) return "P0_EMERGENCY";
            if (value >= SOIL_MOISTURE_P1) return "P1_WARNING";
            if (value >= SOIL_MOISTURE_P2) return "P2_ADVISORY";
        }
        return null;
    }

    /**
     * Get the threshold value for a given severity level and sensor type.
     *
     * @return threshold value, or -1 if unknown
     */
    public static double getThreshold(String sensorType, String severity) {
        if (RAINFALL.equalsIgnoreCase(sensorType)) {
            if ("P0_EMERGENCY".equals(severity)) return RAINFALL_P0;
            if ("P1_WARNING".equals(severity)) return RAINFALL_P1;
            if ("P2_ADVISORY".equals(severity)) return RAINFALL_P2;
        } else if (WATER_LEVEL.equalsIgnoreCase(sensorType)) {
            if ("P0_EMERGENCY".equals(severity)) return WATER_LEVEL_P0;
            if ("P1_WARNING".equals(severity)) return WATER_LEVEL_P1;
            if ("P2_ADVISORY".equals(severity)) return WATER_LEVEL_P2;
        } else if (SOIL_MOISTURE.equalsIgnoreCase(sensorType)) {
            if ("P0_EMERGENCY".equals(severity)) return SOIL_MOISTURE_P0;
            if ("P1_WARNING".equals(severity)) return SOIL_MOISTURE_P1;
            if ("P2_ADVISORY".equals(severity)) return SOIL_MOISTURE_P2;
        }
        return -1;
    }

    /**
     * Extract the first matching flood-relevant value from measurement map.
     * Returns the value and updates the sensorType holder, or returns null
     * if no flood-relevant measurement is found.
     */
    public static FloodReading extractFloodReading(Map<String, Double> measurements, String sensorType) {
        if (measurements == null || measurements.isEmpty()) return null;

        if (RAINFALL.equalsIgnoreCase(sensorType)) {
            Double v = findKeyIgnoreCase(measurements, "rainfall");
            if (v != null) return new FloodReading(RAINFALL, v);
        } else if (WATER_LEVEL.equalsIgnoreCase(sensorType)) {
            Double v = findKeyIgnoreCase(measurements, "water_level");
            if (v != null) return new FloodReading(WATER_LEVEL, v);
        } else if (SOIL_MOISTURE.equalsIgnoreCase(sensorType)) {
            Double v = findKeyIgnoreCase(measurements, "soil_moisture");
            if (v != null) return new FloodReading(SOIL_MOISTURE, v);
        }
        return null;
    }

    private static Double findKeyIgnoreCase(Map<String, Double> map, String key) {
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }

    /**
     * Simple value holder for a flood sensor reading.
     */
    public static class FloodReading {
        public final String sensorType;
        public final double value;

        public FloodReading(String sensorType, double value) {
            this.sensorType = sensorType;
            this.value = value;
        }
    }
}
