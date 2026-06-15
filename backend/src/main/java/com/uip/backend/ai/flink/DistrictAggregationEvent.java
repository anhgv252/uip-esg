package com.uip.backend.ai.flink;

import java.io.Serializable;
import java.util.List;

/**
 * M4-AI-01: Backend-side mirror of the Flink
 * {@code com.uip.flink.ai.DistrictAggregation} record. The Flink job emits
 * JSON with identical field names; Jackson binds the JSON to this POJO so the
 * consumer does not depend on the Flink classpath.
 *
 * <p>One event = all sensor readings for a (tenant, district, sensorType) over
 * one tumbling window, ready for a single batched AI call.</p>
 */
public record DistrictAggregationEvent(
        String tenantId,
        String districtCode,
        String sensorType,
        int count,
        double maxValue,
        double avgValue,
        long windowStart,
        long windowEnd,
        List<SensorSnapshot> sensors
) implements Serializable {

    public record SensorSnapshot(String sensorId, double value, long observedAtMillis)
            implements Serializable { }
}
