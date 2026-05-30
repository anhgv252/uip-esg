package com.uip.flink.flood;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO emitted by FloodAlertJob when a flood pattern is detected.
 * Consumed by FloodAlertConsumer in the monolith backend.
 *
 * Severity levels per TCVN 9386:2012:
 *   P2_ADVISORY  — first threshold breached
 *   P1_WARNING   — second threshold breached
 *   P0_EMERGENCY — critical threshold breached
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FloodAlertEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Sensor device ID */
    private String sensorId;

    /** RAINFALL, WATER_LEVEL, or SOIL_MOISTURE */
    private String sensorType;

    /** Tenant that owns the sensor */
    private String tenantId;

    /** The measured value that triggered the alert */
    private double value;

    /** The threshold that was crossed */
    private double threshold;

    /** P0_EMERGENCY, P1_WARNING, or P2_ADVISORY */
    private String severity;

    /** District from sensor metadata */
    private String district;

    /** Timestamp in millis since epoch */
    private long timestamp;

    /** Number of consecutive readings above threshold (≥ 3 for alert) */
    private int consecutiveCount;
}
