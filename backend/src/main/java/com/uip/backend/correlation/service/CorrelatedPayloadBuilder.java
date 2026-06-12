package com.uip.backend.correlation.service;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.correlation.domain.CorrelatedPayload;
import com.uip.backend.correlation.domain.CorrelatedPayload.SensorReading;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M4-COR-02: Builds a {@link CorrelatedPayload} from a list of correlated {@link AlertEvent}s.
 *
 * <p>For each distinct {@code measureType}, takes the most-recent event by {@code detectedAt}.
 * The incident type is determined by the combination of sensor types present.</p>
 *
 * <p>Incident-type rules (first match wins):
 * <ul>
 *   <li>FLOOD + AQI + NOISE → {@code ENVIRONMENTAL_MULTI_ALERT}</li>
 *   <li>FLOOD + STRUCTURAL  → {@code STRUCTURAL_FLOOD_ALERT}</li>
 *   <li>all others           → {@code MULTI_SENSOR_ALERT}</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
public class CorrelatedPayloadBuilder {

    /**
     * Constructs a {@link CorrelatedPayload} from the given alert events.
     *
     * @param events           list of correlated {@link AlertEvent}s; must not be null or empty
     * @param correlationScore pre-computed correlation score in [0.0, 1.0]
     * @return fully-populated payload ready for AI inference or alerting
     */
    public CorrelatedPayload build(List<AlertEvent> events, double correlationScore) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Cannot build payload from empty event list");
        }

        // Derive window boundaries
        Instant windowStart = events.stream()
                .map(AlertEvent::getDetectedAt)
                .min(Comparator.naturalOrder())
                .orElse(Instant.now());
        Instant windowEnd = events.stream()
                .map(AlertEvent::getDetectedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        // Group by measureType, keep most-recent event per type
        Map<String, AlertEvent> latestByType = events.stream()
                .collect(Collectors.toMap(
                        AlertEvent::getMeasureType,
                        e -> e,
                        (existing, incoming) ->
                                incoming.getDetectedAt().isAfter(existing.getDetectedAt())
                                        ? incoming : existing
                ));

        List<SensorReading> sensors = latestByType.values().stream()
                .map(e -> new SensorReading(
                        e.getSensorId(),
                        e.getMeasureType(),
                        e.getValue() != null ? e.getValue() : 0.0,
                        e.getDetectedAt(),
                        e.getSeverity()))
                .sorted(Comparator.comparing(SensorReading::measureType))
                .collect(Collectors.toList());

        // Determine building and tenant from the first event (all events share building)
        String buildingId = events.get(0).getBuildingId();
        String tenantId   = events.get(0).getTenantId();

        String incidentType = resolveIncidentType(latestByType.keySet());

        log.debug("[PayloadBuilder] building={} sensors={} score={} incidentType={}",
                buildingId, sensors.size(), correlationScore, incidentType);

        return new CorrelatedPayload(
                buildingId,
                tenantId,
                sensors,
                correlationScore,
                windowStart,
                windowEnd,
                incidentType
        );
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Resolves the semantic incident type from the set of distinct sensor measure types.
     *
     * @param measureTypes set of distinct measure type strings, e.g. {@code {"FLOOD", "AQI", "NOISE"}}
     * @return one of {@code ENVIRONMENTAL_MULTI_ALERT}, {@code STRUCTURAL_FLOOD_ALERT},
     *         or {@code MULTI_SENSOR_ALERT}
     */
    String resolveIncidentType(Set<String> measureTypes) {
        boolean hasFlood      = measureTypes.stream().anyMatch(t -> t.toUpperCase().contains("FLOOD"));
        boolean hasAqi        = measureTypes.stream().anyMatch(t -> t.toUpperCase().contains("AQI"));
        boolean hasNoise      = measureTypes.stream().anyMatch(t -> t.toUpperCase().contains("NOISE"));
        boolean hasStructural = measureTypes.stream().anyMatch(t -> t.toUpperCase().contains("STRUCTURAL"));

        if (hasFlood && hasAqi && hasNoise) {
            return "ENVIRONMENTAL_MULTI_ALERT";
        }
        if (hasFlood && hasStructural) {
            return "STRUCTURAL_FLOOD_ALERT";
        }
        return "MULTI_SENSOR_ALERT";
    }
}
