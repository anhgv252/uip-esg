package com.uip.backend.correlation.domain;

import java.time.Instant;
import java.util.List;

/**
 * M4-COR-02: Immutable payload representing a multi-sensor correlated event,
 * ready for AI inference or downstream notification.
 *
 * <p>{@link SensorReading} captures the most recent value per sensor type
 * observed within the correlation window.</p>
 */
public record CorrelatedPayload(
        String buildingId,
        String tenantId,
        List<SensorReading> sensors,
        double correlationScore,
        Instant windowStart,
        Instant windowEnd,
        /** Semantic incident label derived from the contributing sensor types. */
        String incidentType
) {

    /**
     * One sensor's contribution to the correlated incident.
     *
     * @param sensorId    unique sensor identifier
     * @param measureType sensor domain (e.g. "AQI", "FLOOD", "NOISE")
     * @param value       most-recent reading within the correlation window
     * @param timestamp   when this reading was detected
     * @param severity    alert severity inherited from the source {@code AlertEvent}
     */
    public record SensorReading(
            String sensorId,
            String measureType,
            double value,
            Instant timestamp,
            String severity
    ) {}
}
