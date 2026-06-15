package com.uip.flink.correlation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * M4-COR-01: Lightweight POJO mirror of the backend {@code AlertEvent} as it
 * appears on the {@code UIP.flink.alert.detected.v1} topic.
 *
 * <p>The Flink correlation job deserialises alert events into this envelope
 * (it must not depend on the backend JPA classpath). Field names match the
 * JSON keys produced by the backend's alert pipeline.</p>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertEventEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Sensor that triggered the alert. */
    private String sensorId;

    /** Module/domain: ENVIRONMENT, TRAFFIC, BMS, STRUCTURAL, etc. */
    private String module;

    /** Measurement type: AQI, FLOOD, NOISE, WATER_LEVEL, etc. */
    private String measureType;

    private Double value;
    private Double threshold;
    private String severity;

    /** ISO-8601 instant, e.g. "2026-09-26T10:00:00Z". */
    private String detectedAt;

    /** Building the sensor belongs to — the correlation key. */
    private String buildingId;

    /** Tenant id (multi-tenancy isolation). */
    private String tenantId;
}
