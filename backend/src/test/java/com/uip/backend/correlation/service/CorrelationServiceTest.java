package com.uip.backend.correlation.service;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.correlation.domain.CorrelatedIncident;
import com.uip.backend.correlation.flink.IncidentCorrelationConfig;
import com.uip.backend.correlation.repository.CorrelatedIncidentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CorrelationService} — M4-COR-01.
 * Uses {@link SimpleMeterRegistry} (no infrastructure required).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CorrelationService — unit tests")
class CorrelationServiceTest {

    @Mock private CorrelatedIncidentRepository repository;
    @Mock private CorrelationScoringService     scoringService;
    @Mock private IncidentCorrelationConfig     config;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private CorrelationService service;

    @BeforeEach
    void setUp() {
        service = new CorrelationService(config, scoringService, repository, meterRegistry);
        service.initMetrics();

        // Default config
        when(config.getMinSensorTypes()).thenReturn(3);
        when(config.getWindowSeconds()).thenReturn(30);
        when(config.getMinCorrelationScore()).thenReturn(0.6);
    }

    // ─── TC-CS-01: Fewer events than minSensorTypes → empty ──────────────────

    @Test
    @DisplayName("TC-CS-01: Event list smaller than minSensorTypes → empty result")
    void fewerEventsThanMinTypes_returnsEmpty() {
        List<AlertEvent> events = List.of(makeEvent("AQI", "s1"), makeEvent("AQI", "s2"));

        Optional<CorrelatedIncident> result = service.correlate(events, "B-01");

        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    // ─── TC-CS-02: Not enough distinct sensor types → empty ──────────────────

    @Test
    @DisplayName("TC-CS-02: All events same measureType → distinct < min → empty")
    void sameTypeEvents_notEnoughDistinct_returnsEmpty() {
        // 3 events but all AQI → only 1 distinct type
        List<AlertEvent> events = List.of(
                makeEvent("AQI", "s1"),
                makeEvent("AQI", "s2"),
                makeEvent("AQI", "s3")
        );

        Optional<CorrelatedIncident> result = service.correlate(events, "B-01");

        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    // ─── TC-CS-03: Score below threshold → empty ─────────────────────────────

    @Test
    @DisplayName("TC-CS-03: Score below minCorrelationScore → empty result")
    void scoreBelowThreshold_returnsEmpty() {
        List<AlertEvent> events = List.of(
                makeEvent("AQI",        "s1"),
                makeEvent("FLOOD",      "s2"),
                makeEvent("STRUCTURAL", "s3")
        );

        when(scoringService.score(anyInt(), anyInt(), anyLong(), anyInt())).thenReturn(0.4);
        when(scoringService.meetsThreshold(0.4, 0.6)).thenReturn(false);

        Optional<CorrelatedIncident> result = service.correlate(events, "B-01");

        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    // ─── TC-CS-04: Score meets threshold → incident persisted ────────────────

    @Test
    @DisplayName("TC-CS-04: Score meets threshold → incident saved and returned")
    void scoreMeetsThreshold_incidentSaved() {
        List<AlertEvent> events = List.of(
                makeEvent("AQI",        "s1"),
                makeEvent("FLOOD",      "s2"),
                makeEvent("STRUCTURAL", "s3")
        );

        when(scoringService.score(anyInt(), anyInt(), anyLong(), anyInt())).thenReturn(0.85);
        when(scoringService.meetsThreshold(0.85, 0.6)).thenReturn(true);

        CorrelatedIncident saved = new CorrelatedIncident();
        saved.setBuildingId("B-01");
        saved.setCorrelationScore(0.85);
        when(repository.save(any())).thenReturn(saved);

        Optional<CorrelatedIncident> result = service.correlate(events, "B-01");

        assertThat(result).isPresent();
        assertThat(result.get().getBuildingId()).isEqualTo("B-01");
        verify(repository).save(any(CorrelatedIncident.class));
    }

    // ─── TC-CS-05: Null events list → empty ──────────────────────────────────

    @Test
    @DisplayName("TC-CS-05: Null events list → empty (guard clause)")
    void nullEvents_returnsEmpty() {
        Optional<CorrelatedIncident> result = service.correlate(null, "B-01");
        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    // ─── TC-CS-06: processIncomingEvent persists incident from map ────────────

    @Test
    @DisplayName("TC-CS-06: processIncomingEvent maps and saves incoming event")
    void processIncomingEvent_savesIncident() {
        Map<String, Object> event = Map.of(
                "buildingId",       "B-02",
                "sensorTypes",      "[\"AQI\",\"FLOOD\"]",
                "correlationScore", 0.75,
                "eventCount",       5,
                "detectedAt",       "2026-06-12T08:00:00Z"
        );

        CorrelatedIncident saved = new CorrelatedIncident();
        saved.setBuildingId("B-02");
        when(repository.save(any())).thenReturn(saved);

        service.processIncomingEvent(event);

        verify(repository).save(argThat(incident ->
                "B-02".equals(incident.getBuildingId())
                        && incident.getCorrelationScore() == 0.75
                        && incident.getEventCount() == 5
        ));
    }

    // ─── TC-CS-07: processIncomingEvent with null detectedAt uses Instant.now ─

    @Test
    @DisplayName("TC-CS-07: processIncomingEvent without detectedAt defaults to now")
    void processIncomingEvent_missingDetectedAt_usesNow() {
        Map<String, Object> event = Map.of(
                "buildingId",       "B-03",
                "correlationScore", 0.70,
                "eventCount",       3
        );

        CorrelatedIncident saved = new CorrelatedIncident();
        when(repository.save(any())).thenReturn(saved);

        service.processIncomingEvent(event);

        verify(repository).save(argThat(incident ->
                incident.getDetectedAt() != null
        ));
    }

    // ─── TC-CS-08: Metrics incremented correctly ─────────────────────────────

    @Test
    @DisplayName("TC-CS-08: correlate() increments events counter on each call")
    void correlate_incrementsEventsCounter() {
        // Below threshold to avoid repository interaction
        List<AlertEvent> events = List.of(makeEvent("AQI", "s1"));

        service.correlate(events, "B-01");
        service.correlate(events, "B-01");

        double count = meterRegistry.counter(CorrelationService.METRIC_EVENTS_TOTAL).count();
        assertThat(count).isEqualTo(2.0);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AlertEvent makeEvent(String measureType, String sensorId) {
        AlertEvent e = new AlertEvent();
        e.setSensorId(sensorId);
        e.setMeasureType(measureType);
        e.setValue(100.0);
        e.setThreshold(80.0);
        e.setSeverity("HIGH");
        e.setDetectedAt(Instant.now());
        e.setBuildingId("B-01");
        e.setTenantId("tenant-abc");
        e.setModule("test");
        return e;
    }
}
