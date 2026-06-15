package com.uip.flink.correlation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * M4-COR-01: Output of the Flink CEP correlation job — a group of alert events
 * from ≥ {@code minSensorTypes} distinct measure types within one building,
 * emitted to the {@code correlated.incidents} topic.
 *
 * <p>Field names match the keys read by backend
 * {@code CorrelationService.processIncomingEvent}:
 * {@code buildingId}, {@code sensorTypes}, {@code correlationScore},
 * {@code eventCount}, {@code detectedAt}, {@code status}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorrelatedIncidentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String buildingId;
    /** JSON array string, e.g. {@code ["AQI","FLOOD","NOISE"]}. */
    private String sensorTypes;
    private double correlationScore;
    private int eventCount;
    /** ISO-8601 instant string. */
    private String detectedAt;
    private String status;

    /** Distinct measure types in sorted order (for scoring + payload). */
    private List<String> distinctTypes;
    /** Tenant id propagated from the contributing alerts. */
    private String tenantId;
}
