package com.uip.flink.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * M4-AI-01: Aggregated sensor readings for one (tenant, district, sensorType)
 * over a tumbling window, emitted by {@link DistrictAggregationJob} to the
 * {@code ai.district.aggregations} topic.
 *
 * <p>Replaces N per-reading AI calls with a single batched call per
 * district/sensor-type/window — the core of the "600K → 50 calls/min" cost
 * reduction. Consumed by the backend {@code DistrictAggregationConsumer},
 * which delegates to {@code AiInferenceService}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistrictAggregation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Tenant identifier (multi-tenancy isolation key). */
    private String tenantId;

    /** District code, e.g. "HCM-D1". */
    private String districtCode;

    /** Sensor type, e.g. "AQI", "WATER_LEVEL", "NOISE". */
    private String sensorType;

    /** Number of sensor readings aggregated in this window. */
    private int count;

    /** Maximum reading value observed in the window. */
    private double maxValue;

    /** Average (mean) reading value across the window. */
    private double avgValue;

    /** Window start epoch millis. */
    private long windowStart;

    /** Window end epoch millis. */
    private long windowEnd;

    /**
     * Up to {@code maxSensorsPerDistrict} individual sensor snapshots
     * (capped to bound memory). Empty list if cap is zero.
     */
    private List<SensorSnapshot> sensors;

    /** Single sensor reading retained for context / debugging. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensorSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        private String sensorId;
        private double value;
        private long observedAtMillis;
    }
}
