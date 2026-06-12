package com.uip.backend.correlation;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.correlation.domain.CorrelatedIncident;
import com.uip.backend.correlation.flink.IncidentCorrelationConfig;
import com.uip.backend.correlation.repository.CorrelatedIncidentRepository;
import com.uip.backend.correlation.service.CorrelationScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M4-COR-01: Correlation Engine — unit tests using CorrelationScoringService directly.
 *
 * <p>CorrelationService is being implemented by the parallel backend sprint; this test class
 * exercises the correlation <em>decision logic</em> through CorrelationScoringService and
 * IncidentCorrelationConfig, simulating the pipeline that CorrelationService will eventually
 * encapsulate. When CorrelationService ships, these tests can be refactored to call it directly.</p>
 *
 * <p>Score formula (from CorrelationScoringService):
 * <pre>
 *   typeCoverage  = distinctTypes / minRequired
 *   timeSpread    = 1 − (timeRangeSeconds / windowSeconds)
 *   score         = min(1.0,  typeCoverage × max(0.1, timeSpread))
 * </pre>
 * </p>
 *
 * <p>Default config: windowSeconds=30, minSensorTypes=3, minCorrelationScore=0.6</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Correlation Engine E2E — scoring + incident creation")
class CorrelationE2ETest {

    // Real scoring service spy so we can stub score() in specific tests
    @Spy
    private CorrelationScoringService scoringService = new CorrelationScoringService();

    @Mock
    private CorrelatedIncidentRepository incidentRepository;

    @Captor
    private ArgumentCaptor<CorrelatedIncident> incidentCaptor;

    private IncidentCorrelationConfig config;

    // ─── Base timestamp for deterministic time calculations ─────────────────────
    private static final Instant BASE = Instant.parse("2026-09-26T10:00:00Z");

    @BeforeEach
    void setUp() {
        config = new IncidentCorrelationConfig();
        // defaults: windowSeconds=30, minSensorTypes=3, minCorrelationScore=0.6
        when(incidentRepository.save(any(CorrelatedIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Test 1 — 3 sensor types in 30s window → correlated incident created
    // score(3, 3, 4, 30) = 1.0 × (1 − 4/30) = 1.0 × 0.867 = 0.867 ≥ 0.6
    // ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("3 diverse sensor events within 30s window → correlated incident created and saved")
    void multiSensorCorrelation_createsIncident() {
        List<AlertEvent> events = Arrays.asList(
                buildEvent("B001", "AQI",         250.0, BASE),
                buildEvent("B001", "WATER_LEVEL",   1.8, BASE.plusSeconds(2)),
                buildEvent("B001", "NOISE",          95.0, BASE.plusSeconds(4))
        );

        Optional<CorrelatedIncident> result = simulateCorrelation(events, "B001");

        assertThat(result).isPresent();
        verify(incidentRepository, times(1)).save(any(CorrelatedIncident.class));
        assertThat(result.get().getCorrelationScore()).isGreaterThanOrEqualTo(0.6);
        assertThat(result.get().getEventCount()).isEqualTo(3);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Test 2 — 5 sensor types in quick succession → high-score incident
    // score(5, 3, 2, 30) = min(1.0, 1.667 × (1 − 2/30)) = min(1.0, 1.556) = 1.0
    // ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("5 distinct sensor types within 2s → incident with score capped at 1.0")
    void fiveSensorCorrelation_createsHighScoreIncident() {
        List<AlertEvent> events = Arrays.asList(
                buildEvent("B001", "AQI",           310.0, BASE),
                buildEvent("B001", "WATER_LEVEL",     2.1, BASE.plusSeconds(1)),
                buildEvent("B001", "NOISE",           88.0, BASE.plusSeconds(1)),
                buildEvent("B001", "TEMPERATURE",     42.0, BASE.plusSeconds(2)),
                buildEvent("B001", "CO2",            900.0, BASE.plusSeconds(2))
        );

        Optional<CorrelatedIncident> result = simulateCorrelation(events, "B001");

        assertThat(result).isPresent();
        // With 5 types / min 3, score is capped at 1.0
        assertThat(result.get().getCorrelationScore()).isEqualTo(1.0);
        assertThat(result.get().getEventCount()).isEqualTo(5);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Test 3 — single sensor event → distinctTypes=1 → score=0.333 < 0.6 → empty
    // score(1, 3, 0, 30) = 0.333 × 1.0 = 0.333
    // ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Single sensor event → below minSensorTypes=3 → returns empty, no save")
    void singleSensor_noCorrelation() {
        List<AlertEvent> events = Collections.singletonList(
                buildEvent("B001", "AQI", 250.0, BASE)
        );

        Optional<CorrelatedIncident> result = simulateCorrelation(events, "B001");

        assertThat(result).isEmpty();
        verifyNoInteractions(incidentRepository);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Test 4 — 2 sensor types spread over 5s → score=0.556 < 0.6 → empty
    // score(2, 3, 5, 30) = 0.667 × (1 − 5/30) = 0.667 × 0.833 = 0.556
    // ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("2 sensor types, 5s spread → score 0.556 below threshold → returns empty")
    void twoSensors_noCorrelation() {
        List<AlertEvent> events = Arrays.asList(
                buildEvent("B001", "AQI",         200.0, BASE),
                buildEvent("B001", "WATER_LEVEL",   1.5, BASE.plusSeconds(5))
        );

        Optional<CorrelatedIncident> result = simulateCorrelation(events, "B001");

        assertThat(result).isEmpty();
        verifyNoInteractions(incidentRepository);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Test 5 — diversity boost: 3 distinct types vs 3 same type → higher score
    // 3 distinct: score(3, 3, 5, 30) = 1.0 × 0.833 = 0.833
    // 3 same:     score(1, 3, 5, 30) = 0.333 × 0.833 = 0.278
    // ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("3 distinct sensor types score higher than 3 events of the same type")
    void differentSensorTypes_diversityBoost() {
        Instant t0 = BASE;
        Instant t1 = BASE.plusSeconds(2);
        Instant t2 = BASE.plusSeconds(5);

        // 3 distinct types
        List<AlertEvent> diverseEvents = Arrays.asList(
                buildEvent("B001", "AQI",         250.0, t0),
                buildEvent("B001", "WATER_LEVEL",   1.8, t1),
                buildEvent("B001", "NOISE",          95.0, t2)
        );

        // 3 events of same type (all AQI)
        List<AlertEvent> sameTypeEvents = Arrays.asList(
                buildEvent("B001", "AQI", 250.0, t0),
                buildEvent("B001", "AQI", 255.0, t1),
                buildEvent("B001", "AQI", 260.0, t2)
        );

        double diverseScore = computeScore(diverseEvents);
        double sameTypeScore = computeScore(sameTypeEvents);

        assertThat(diverseScore).isGreaterThan(sameTypeScore);
        // Diverse should meet threshold, same-type should not
        assertThat(scoringService.meetsThreshold(diverseScore, config.getMinCorrelationScore())).isTrue();
        assertThat(scoringService.meetsThreshold(sameTypeScore, config.getMinCorrelationScore())).isFalse();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Test 6 — empty event list → short-circuit returns empty
    // ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Empty event list → immediate empty Optional, no scoring, no save")
    void emptyEventList_returnsEmpty() {
        Optional<CorrelatedIncident> result = simulateCorrelation(Collections.emptyList(), "B001");

        assertThat(result).isEmpty();
        verifyNoInteractions(incidentRepository);
        // scoringService.score() must never be called for empty input
        verify(scoringService, never()).score(anyInt(), anyInt(), anyLong(), anyInt());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Test 7 — score forced below minCorrelationScore=0.6 → no incident
    // Uses @Spy to stub score() returning 0.3 simulating a low-confidence window
    // ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Score below minCorrelationScore (0.3 < 0.6) → no incident, no save")
    void belowMinScore_noIncident() {
        doReturn(0.3).when(scoringService).score(anyInt(), anyInt(), anyLong(), anyInt());

        List<AlertEvent> events = Arrays.asList(
                buildEvent("B001", "AQI",         250.0, BASE),
                buildEvent("B001", "WATER_LEVEL",   1.8, BASE.plusSeconds(2)),
                buildEvent("B001", "NOISE",          95.0, BASE.plusSeconds(4))
        );

        Optional<CorrelatedIncident> result = simulateCorrelation(events, "B001");

        assertThat(result).isEmpty();
        verifyNoInteractions(incidentRepository);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Test 8 — persisted incident must carry correct buildingId
    // ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Persisted incident carries buildingId of the source events")
    void incidentStoredWithCorrectBuildingId() {
        final String targetBuilding = "B001";
        List<AlertEvent> events = Arrays.asList(
                buildEvent(targetBuilding, "AQI",         250.0, BASE),
                buildEvent(targetBuilding, "WATER_LEVEL",   1.8, BASE.plusSeconds(3)),
                buildEvent(targetBuilding, "NOISE",          95.0, BASE.plusSeconds(5))
        );

        simulateCorrelation(events, targetBuilding);

        verify(incidentRepository).save(incidentCaptor.capture());
        CorrelatedIncident saved = incidentCaptor.getValue();
        assertThat(saved.getBuildingId()).isEqualTo(targetBuilding);
        assertThat(saved.getCorrelationScore()).isGreaterThanOrEqualTo(0.6);
        assertThat(saved.getStatus()).isEqualTo("OPEN");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Helpers — simulate the pipeline logic CorrelationService will eventually own
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Simulates the CorrelationService.correlate() decision:
     * 1. Guard on empty list.
     * 2. Count distinct sensor types (measureType).
     * 3. Compute time spread (last - first detectedAt).
     * 4. Score via CorrelationScoringService.score().
     * 5. If score >= minCorrelationScore → persist CorrelatedIncident and return it.
     */
    private Optional<CorrelatedIncident> simulateCorrelation(List<AlertEvent> events,
                                                              String buildingId) {
        if (events.isEmpty()) {
            return Optional.empty();
        }

        int distinctTypes = (int) events.stream()
                .map(AlertEvent::getMeasureType)
                .distinct()
                .count();

        Instant first = events.stream()
                .map(AlertEvent::getDetectedAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        Instant last = events.stream()
                .map(AlertEvent::getDetectedAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        long timeRangeSeconds = last.getEpochSecond() - first.getEpochSecond();

        double score = scoringService.score(
                distinctTypes,
                config.getMinSensorTypes(),
                timeRangeSeconds,
                config.getWindowSeconds());

        if (!scoringService.meetsThreshold(score, config.getMinCorrelationScore())) {
            return Optional.empty();
        }

        CorrelatedIncident incident = new CorrelatedIncident();
        incident.setBuildingId(buildingId);
        incident.setCorrelationScore(score);
        incident.setEventCount(events.size());
        incident.setSensorTypes(
                events.stream()
                        .map(AlertEvent::getMeasureType)
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining(",")));

        incidentRepository.save(incident);
        return Optional.of(incident);
    }

    /**
     * Compute raw score for a list of events — used for comparison tests.
     */
    private double computeScore(List<AlertEvent> events) {
        int distinctTypes = (int) events.stream()
                .map(AlertEvent::getMeasureType)
                .distinct()
                .count();

        Instant first = events.stream()
                .map(AlertEvent::getDetectedAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        Instant last = events.stream()
                .map(AlertEvent::getDetectedAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        long timeRangeSeconds = last.getEpochSecond() - first.getEpochSecond();

        return scoringService.score(
                distinctTypes,
                config.getMinSensorTypes(),
                timeRangeSeconds,
                config.getWindowSeconds());
    }

    /**
     * Builder for AlertEvent test fixtures.
     * Sets all @Column(nullable=false) fields to valid defaults so JPA mapping is
     * satisfied when tests run with a real context (Testcontainers IT).
     */
    private AlertEvent buildEvent(String buildingId, String measureType,
                                   double value, Instant detectedAt) {
        AlertEvent e = new AlertEvent();
        e.setBuildingId(buildingId);
        e.setMeasureType(measureType);
        e.setValue(value);
        e.setThreshold(100.0);
        e.setSensorId("SENSOR-" + measureType + "-001");
        e.setDetectedAt(detectedAt);
        e.setSeverity("HIGH");
        e.setModule("environment");
        return e;
    }
}
