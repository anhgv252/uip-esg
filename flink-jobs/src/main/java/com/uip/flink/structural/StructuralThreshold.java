package com.uip.flink.structural;

import java.util.Map;
import java.util.Set;

/**
 * TCVN 9386:2012 + ISO 4866 threshold definitions for structural sensors.
 *
 * <p>Provides WARNING and CRITICAL thresholds for each structural sensor type,
 * following the Vietnamese building safety standard.</p>
 */
public final class StructuralThreshold {

    private StructuralThreshold() {}

    private static final Set<String> STRUCTURAL_SENSOR_TYPES = Set.of(
            "STRUCTURAL_VIBRATION",
            "STRUCTURAL_TILT",
            "STRUCTURAL_CRACK"
    );

    private static final Map<String, Thresholds> THRESHOLDS = Map.of(
            "STRUCTURAL_VIBRATION", new Thresholds(10.0, 50.0, "mm/s"),
            "STRUCTURAL_TILT",      new Thresholds(3.0, 10.0, "mrad"),
            "STRUCTURAL_CRACK",     new Thresholds(0.3, 2.0, "mm")
    );

    record Thresholds(double warning, double critical, String unit) {}

    /** Check if the sensor type is a structural sensor we monitor. */
    public static boolean isStructuralSensor(String sensorType) {
        return STRUCTURAL_SENSOR_TYPES.contains(sensorType);
    }

    /** Get the WARNING (P1) threshold for a sensor type. Returns MAX_VALUE if unknown. */
    public static double getWarningThreshold(String sensorType) {
        Thresholds t = THRESHOLDS.get(sensorType);
        return t != null ? t.warning() : Double.MAX_VALUE;
    }

    /** Get the CRITICAL (P0) threshold for a sensor type. Returns MAX_VALUE if unknown. */
    public static double getCriticalThreshold(String sensorType) {
        Thresholds t = THRESHOLDS.get(sensorType);
        return t != null ? t.critical() : Double.MAX_VALUE;
    }

    /** Get the measurement unit for a sensor type. Returns empty string if unknown. */
    public static String getUnit(String sensorType) {
        Thresholds t = THRESHOLDS.get(sensorType);
        return t != null ? t.unit() : "";
    }

    /**
     * Classify severity based on sensor type and measured value.
     *
     * @return "CRITICAL", "WARNING", or {@code null} if below warning threshold
     */
    public static String classifySeverity(String sensorType, double value) {
        Thresholds t = THRESHOLDS.get(sensorType);
        if (t == null) return null;
        if (value >= t.critical()) return "CRITICAL";
        if (value >= t.warning()) return "WARNING";
        return null;
    }
}
