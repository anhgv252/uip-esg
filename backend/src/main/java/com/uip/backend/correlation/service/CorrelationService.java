package com.uip.backend.correlation.service;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.correlation.domain.CorrelatedIncident;
import com.uip.backend.correlation.flink.IncidentCorrelationConfig;
import com.uip.backend.correlation.repository.CorrelatedIncidentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * M4-COR-01: Core service for multi-sensor incident correlation.
 *
 * <p>Two entry points:
 * <ol>
 *   <li>{@link #correlate} — in-process path: receives a list of {@link AlertEvent}s
 *       from the CEP pipeline, computes a correlation score, and persists a
 *       {@link CorrelatedIncident} if the score meets the configured threshold.</li>
 *   <li>{@link #processIncomingEvent} — Kafka path: called by {@code CorrelationDlqHandler}
 *       to persist a correlated incident event already produced by Flink.</li>
 * </ol>
 * </p>
 *
 * <p>Micrometer metrics:
 * <ul>
 *   <li>{@code correlation_events_total} — every call to {@link #correlate}</li>
 *   <li>{@code correlation_incidents_created_total} — incidents that pass the threshold</li>
 *   <li>{@code correlation_score_histogram} — distribution of computed scores</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CorrelationService {

    private final IncidentCorrelationConfig    config;
    private final CorrelationScoringService    scoringService;
    private final CorrelatedIncidentRepository repository;
    private final MeterRegistry               meterRegistry;

    // ─── Metric names ─────────────────────────────────────────────────────────

    static final String METRIC_EVENTS_TOTAL    = "correlation_events_total";
    static final String METRIC_INCIDENTS_TOTAL = "correlation_incidents_created_total";
    static final String METRIC_SCORE_HISTOGRAM = "correlation_score_histogram";

    @PostConstruct
    void initMetrics() {
        // Pre-register counters so they appear in Prometheus output even before first event
        meterRegistry.counter(METRIC_EVENTS_TOTAL,
                "description", "Total correlation evaluation requests");
        meterRegistry.counter(METRIC_INCIDENTS_TOTAL,
                "description", "Total correlated incidents persisted");
        meterRegistry.summary(METRIC_SCORE_HISTOGRAM);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Evaluates a window of {@link AlertEvent}s and, if the correlation score meets the
     * configured threshold, persists and returns a {@link CorrelatedIncident}.
     *
     * @param events     alert events within the CEP time window; must not be null
     * @param buildingId building context for the incident
     * @return the persisted incident, or {@link Optional#empty()} if the score is below threshold
     *         or fewer than {@code minSensorTypes} distinct types are present
     */
    @Transactional
    public Optional<CorrelatedIncident> correlate(List<AlertEvent> events, String buildingId) {
        meterRegistry.counter(METRIC_EVENTS_TOTAL).increment();

        if (events == null || events.size() < config.getMinSensorTypes()) {
            log.debug("[Correlation] Skipped: event count {} < minSensorTypes {}",
                    events == null ? 0 : events.size(), config.getMinSensorTypes());
            return Optional.empty();
        }

        List<String> distinctTypes = events.stream()
                .map(AlertEvent::getMeasureType)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        if (distinctTypes.size() < config.getMinSensorTypes()) {
            log.debug("[Correlation] Skipped: distinct types {} < minSensorTypes {}",
                    distinctTypes.size(), config.getMinSensorTypes());
            return Optional.empty();
        }

        Instant earliest = events.stream()
                .map(AlertEvent::getDetectedAt)
                .min(Comparator.naturalOrder())
                .orElse(Instant.now());
        Instant latest = events.stream()
                .map(AlertEvent::getDetectedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        long timeRangeSeconds = latest.getEpochSecond() - earliest.getEpochSecond();

        double score = scoringService.score(
                distinctTypes.size(),
                config.getMinSensorTypes(),
                timeRangeSeconds,
                config.getWindowSeconds()
        );

        meterRegistry.summary(METRIC_SCORE_HISTOGRAM).record(score);

        log.info("[Correlation] building={} distinctTypes={} timeRange={}s score={}",
                buildingId, distinctTypes, timeRangeSeconds, score);

        if (!scoringService.meetsThreshold(score, config.getMinCorrelationScore())) {
            log.debug("[Correlation] Score {} below threshold {}", score, config.getMinCorrelationScore());
            return Optional.empty();
        }

        String sensorTypesJson = buildSensorTypesJson(distinctTypes);

        CorrelatedIncident incident = new CorrelatedIncident();
        incident.setBuildingId(buildingId);
        incident.setSensorTypes(sensorTypesJson);
        incident.setCorrelationScore(score);
        incident.setEventCount(events.size());
        incident.setDetectedAt(Instant.now());

        CorrelatedIncident saved = repository.save(incident);
        meterRegistry.counter(METRIC_INCIDENTS_TOTAL).increment();

        log.info("[Correlation] Incident persisted: id={} building={} score={} types={}",
                saved.getId(), buildingId, score, sensorTypesJson);

        return Optional.of(saved);
    }

    /**
     * Persists an incoming correlated incident event received from the Kafka topic
     * (produced by Flink). Called by {@code CorrelationDlqHandler}.
     *
     * @param event raw event map deserialized from Kafka message JSON
     */
    @Transactional
    public void processIncomingEvent(Map<String, Object> event) {
        meterRegistry.counter(METRIC_EVENTS_TOTAL).increment();

        CorrelatedIncident incident = new CorrelatedIncident();
        incident.setBuildingId(getString(event, "buildingId"));
        incident.setSensorTypes(getString(event, "sensorTypes"));

        Object rawScore = event.get("correlationScore");
        incident.setCorrelationScore(rawScore instanceof Number n ? n.doubleValue() : 0.0);

        Object rawCount = event.get("eventCount");
        incident.setEventCount(rawCount instanceof Number n ? n.intValue() : 0);

        String detectedAtStr = getString(event, "detectedAt");
        incident.setDetectedAt(
                detectedAtStr != null ? Instant.parse(detectedAtStr) : Instant.now());

        String status = getString(event, "status");
        if (status != null) {
            incident.setStatus(status);
        }

        CorrelatedIncident saved = repository.save(incident);
        meterRegistry.counter(METRIC_INCIDENTS_TOTAL).increment();

        log.info("[Correlation] Kafka event persisted: id={} building={}",
                saved.getId(), saved.getBuildingId());
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String buildSensorTypesJson(List<String> types) {
        return "[\"" + String.join("\",\"", types) + "\"]";
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
