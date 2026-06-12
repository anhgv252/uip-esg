package com.uip.backend.correlation.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted result of a Flink CEP correlation window — a group of sensor events
 * from multiple sensor types within a time window that collectively exceed the
 * correlation score threshold.
 */
@Entity
@Table(name = "correlated_incidents")
@Data
@NoArgsConstructor
public class CorrelatedIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "building_id", length = 100)
    private String buildingId;

    /**
     * JSON array of distinct sensor types that contributed to this incident,
     * e.g. {@code ["AQI","FLOOD","NOISE"]}.
     */
    @Column(name = "sensor_types", columnDefinition = "text")
    private String sensorTypes;

    @Column(name = "correlation_score", nullable = false)
    private double correlationScore;

    /** Lifecycle status: OPEN or RESOLVED. */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "event_count", nullable = false)
    private int eventCount;
}
