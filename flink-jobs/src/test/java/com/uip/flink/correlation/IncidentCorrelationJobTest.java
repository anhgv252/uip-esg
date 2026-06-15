package com.uip.flink.correlation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * M4-COR-01: Unit tests for {@link IncidentCorrelationJob} helpers and the
 * extracted {@link IncidentCorrelationJob#evaluateWindow} decision function.
 *
 * <p>Pure-POJO tests (no MiniCluster, no Context stub).</p>
 */
@DisplayName("IncidentCorrelationJob — CEP correlation")
class IncidentCorrelationJobTest {

    private static final int WINDOW = 30;

    // ─── Scoring (mirrors backend CorrelationScoringService) ─────────────────

    @Test
    @DisplayName("score: 3 distinct types, simultaneous → ~1.0")
    void score_threeTypesSimultaneous_nearOne() {
        double s = IncidentCorrelationJob.score(3, 3, 0, WINDOW);
        assertThat(s).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("score: 2 distinct types (< minRequired=3) → 0.667")
    void score_twoTypes_belowFullCoverage() {
        double s = IncidentCorrelationJob.score(2, 3, 0, WINDOW);
        assertThat(s).isCloseTo(0.6667, within(0.001));
    }

    @Test
    @DisplayName("score: events spread across full window → timeSpread floored to 0.1")
    void score_fullWindowSpread_flooredToMinTimeSpread() {
        double s = IncidentCorrelationJob.score(3, 3, WINDOW, WINDOW);
        // timeSpread = 1 - 30/30 = 0 → floored to 0.1 → score = 1.0 * 0.1 = 0.1
        assertThat(s).isCloseTo(0.1, within(0.001));
    }

    // ─── Parser ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseAlert: valid JSON → envelope populated")
    void parseAlert_validJson() {
        String json = "{\"sensorId\":\"s1\",\"measureType\":\"AQI\",\"buildingId\":\"B1\",\"detectedAt\":\"2026-09-26T10:00:00Z\"}";
        AlertEventEnvelope e = IncidentCorrelationJob.parseAlert(json);
        assertThat(e).isNotNull();
        assertThat(e.getMeasureType()).isEqualTo("AQI");
        assertThat(e.getBuildingId()).isEqualTo("B1");
    }

    @Test
    @DisplayName("parseAlert: malformed/blank/null → null (no throw)")
    void parseAlert_malformed_returnsNull() {
        assertThat(IncidentCorrelationJob.parseAlert("{not json")).isNull();
        assertThat(IncidentCorrelationJob.parseAlert("")).isNull();
        assertThat(IncidentCorrelationJob.parseAlert(null)).isNull();
    }

    // ─── sensorTypes JSON ────────────────────────────────────────────────────

    @Test
    @DisplayName("buildSensorTypesJson: sorted distinct types → JSON array string")
    void buildSensorTypesJson_sorted() {
        String json = IncidentCorrelationJob.buildSensorTypesJson(List.of("AQI", "FLOOD", "NOISE"));
        assertThat(json).isEqualTo("[\"AQI\",\"FLOOD\",\"NOISE\"]");
    }

    // ─── evaluateWindow (decision logic) ─────────────────────────────────────

    @Test
    @DisplayName("evaluateWindow: ≥3 distinct types + high score → emits incident")
    void evaluateWindow_threeDistinctTypes_emitsIncident() {
        Optional<CorrelatedIncidentEvent> result = IncidentCorrelationJob.evaluateWindow(List.of(
                alert("B1", "AQI", "2026-09-26T10:00:00Z"),
                alert("B1", "FLOOD", "2026-09-26T10:00:02Z"),
                alert("B1", "NOISE", "2026-09-26T10:00:04Z")
        ), 3, 0.6, WINDOW);

        assertThat(result).isPresent();
        CorrelatedIncidentEvent inc = result.get();
        assertThat(inc.getBuildingId()).isEqualTo("B1");
        assertThat(inc.getEventCount()).isEqualTo(3);
        assertThat(inc.getSensorTypes()).isEqualTo("[\"AQI\",\"FLOOD\",\"NOISE\"]");
        assertThat(inc.getCorrelationScore()).isGreaterThanOrEqualTo(0.6);
        assertThat(inc.getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("evaluateWindow: <3 distinct types → empty")
    void evaluateWindow_twoDistinctTypes_empty() {
        Optional<CorrelatedIncidentEvent> result = IncidentCorrelationJob.evaluateWindow(List.of(
                alert("B1", "AQI", "2026-09-26T10:00:00Z"),
                alert("B1", "AQI", "2026-09-26T10:00:02Z"),
                alert("B1", "FLOOD", "2026-09-26T10:00:04Z")
        ), 3, 0.6, WINDOW);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("evaluateWindow: score below threshold (full window spread) → empty")
    void evaluateWindow_lowScore_empty() {
        Optional<CorrelatedIncidentEvent> result = IncidentCorrelationJob.evaluateWindow(List.of(
                alert("B1", "AQI", "2026-09-26T10:00:00Z"),
                alert("B1", "FLOOD", "2026-09-26T10:00:15Z"),
                alert("B1", "NOISE", "2026-09-26T10:00:30Z")
        ), 3, 0.6, WINDOW);
        // score ~0.1 < 0.6
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("evaluateWindow: single sensor type repeated → empty (distinct < min)")
    void evaluateWindow_repeatedSingleType_empty() {
        Optional<CorrelatedIncidentEvent> result = IncidentCorrelationJob.evaluateWindow(List.of(
                alert("B1", "AQI", "2026-09-26T10:00:00Z"),
                alert("B1", "AQI", "2026-09-26T10:00:01Z"),
                alert("B1", "AQI", "2026-09-26T10:00:02Z")
        ), 3, 0.6, WINDOW);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("evaluateWindow: null/empty alerts → empty (no NPE)")
    void evaluateWindow_nullOrEmpty_empty() {
        assertThat(IncidentCorrelationJob.evaluateWindow(null, 3, 0.6, WINDOW)).isEmpty();
        assertThat(IncidentCorrelationJob.evaluateWindow(List.of(), 3, 0.6, WINDOW)).isEmpty();
    }

    @Test
    @DisplayName("evaluateWindow: 5+ distinct types, tight cluster → emits with score 1.0")
    void evaluateWindow_fiveDistinctTypes_emits() {
        Optional<CorrelatedIncidentEvent> result = IncidentCorrelationJob.evaluateWindow(List.of(
                alert("B2", "AQI", "2026-09-26T10:00:00Z"),
                alert("B2", "FLOOD", "2026-09-26T10:00:01Z"),
                alert("B2", "NOISE", "2026-09-26T10:00:01Z"),
                alert("B2", "WATER_LEVEL", "2026-09-26T10:00:02Z"),
                alert("B2", "STRUCTURAL", "2026-09-26T10:00:02Z")
        ), 3, 0.6, WINDOW);

        assertThat(result).isPresent();
        assertThat(result.get().getEventCount()).isEqualTo(5);
        assertThat(result.get().getDistinctTypes()).hasSize(5);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static AlertEventEnvelope alert(String building, String type, String detectedAt) {
        AlertEventEnvelope e = new AlertEventEnvelope();
        e.setBuildingId(building);
        e.setMeasureType(type);
        e.setDetectedAt(detectedAt);
        e.setSensorId("sensor-" + type);
        return e;
    }
}
