package com.uip.backend.correlation.service;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.correlation.domain.CorrelatedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link CorrelatedPayloadBuilder} — M4-COR-02.
 * No Spring context; plain instantiation.
 */
@DisplayName("CorrelatedPayloadBuilder — unit tests")
class CorrelatedPayloadBuilderTest {

    private CorrelatedPayloadBuilder builder;

    private static final Instant BASE = Instant.parse("2026-06-12T09:00:00Z");

    @BeforeEach
    void setUp() {
        builder = new CorrelatedPayloadBuilder();
    }

    // ─── TC-CPB-01: Basic payload built correctly ─────────────────────────────

    @Test
    @DisplayName("TC-CPB-01: build() populates buildingId, tenantId, correlationScore")
    void build_populatesTopLevelFields() {
        List<AlertEvent> events = List.of(
                makeEvent("AQI",   "s1", 120.0, BASE,                   "B-01", "t1", "HIGH"),
                makeEvent("FLOOD", "s2",   2.5, BASE.plusSeconds(5),    "B-01", "t1", "CRITICAL")
        );

        CorrelatedPayload payload = builder.build(events, 0.75);

        assertThat(payload.buildingId()).isEqualTo("B-01");
        assertThat(payload.tenantId()).isEqualTo("t1");
        assertThat(payload.correlationScore()).isEqualTo(0.75);
    }

    // ─── TC-CPB-02: Window boundaries from min/max detectedAt ────────────────

    @Test
    @DisplayName("TC-CPB-02: windowStart = earliest detectedAt, windowEnd = latest detectedAt")
    void build_windowBoundariesCorrect() {
        Instant early = BASE;
        Instant late  = BASE.plusSeconds(20);
        List<AlertEvent> events = List.of(
                makeEvent("AQI",   "s1", 90.0, late,  "B-01", "t1", "HIGH"),
                makeEvent("FLOOD", "s2",  1.5, early, "B-01", "t1", "HIGH")
        );

        CorrelatedPayload payload = builder.build(events, 0.8);

        assertThat(payload.windowStart()).isEqualTo(early);
        assertThat(payload.windowEnd()).isEqualTo(late);
    }

    // ─── TC-CPB-03: Latest reading per measureType is kept ───────────────────

    @Test
    @DisplayName("TC-CPB-03: Duplicate measureType → most-recent reading retained")
    void build_duplicateMeasureType_keepsMostRecent() {
        Instant older = BASE;
        Instant newer = BASE.plusSeconds(10);
        List<AlertEvent> events = List.of(
                makeEvent("AQI", "s1", 100.0, older, "B-01", "t1", "HIGH"),
                makeEvent("AQI", "s2", 150.0, newer, "B-01", "t1", "CRITICAL")
        );

        CorrelatedPayload payload = builder.build(events, 0.7);

        assertThat(payload.sensors()).hasSize(1);
        assertThat(payload.sensors().get(0).value()).isCloseTo(150.0, within(0.01));
        assertThat(payload.sensors().get(0).sensorId()).isEqualTo("s2");
    }

    // ─── TC-CPB-04: FLOOD+AQI+NOISE → ENVIRONMENTAL_MULTI_ALERT ─────────────

    @Test
    @DisplayName("TC-CPB-04: FLOOD + AQI + NOISE → ENVIRONMENTAL_MULTI_ALERT")
    void resolveIncidentType_floodAqiNoise_environmentalMultiAlert() {
        String type = builder.resolveIncidentType(Set.of("FLOOD", "AQI", "NOISE"));
        assertThat(type).isEqualTo("ENVIRONMENTAL_MULTI_ALERT");
    }

    // ─── TC-CPB-05: FLOOD+STRUCTURAL → STRUCTURAL_FLOOD_ALERT ────────────────

    @Test
    @DisplayName("TC-CPB-05: FLOOD + STRUCTURAL → STRUCTURAL_FLOOD_ALERT")
    void resolveIncidentType_floodStructural_structuralFloodAlert() {
        String type = builder.resolveIncidentType(Set.of("FLOOD", "STRUCTURAL"));
        assertThat(type).isEqualTo("STRUCTURAL_FLOOD_ALERT");
    }

    // ─── TC-CPB-06: Other combinations → MULTI_SENSOR_ALERT ─────────────────

    @Test
    @DisplayName("TC-CPB-06: AQI + HUMIDITY only → MULTI_SENSOR_ALERT (default)")
    void resolveIncidentType_otherCombination_multiSensorAlert() {
        String type = builder.resolveIncidentType(Set.of("AQI", "HUMIDITY"));
        assertThat(type).isEqualTo("MULTI_SENSOR_ALERT");
    }

    // ─── TC-CPB-07: Empty list → IllegalArgumentException ────────────────────

    @Test
    @DisplayName("TC-CPB-07: build() with empty list → IllegalArgumentException")
    void build_emptyList_throwsException() {
        assertThatThrownBy(() -> builder.build(List.of(), 0.8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-CPB-08: build() with null list → IllegalArgumentException")
    void build_nullList_throwsException() {
        assertThatThrownBy(() -> builder.build(null, 0.8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── TC-CPB-09: Sensor readings include severity from source event ────────

    @Test
    @DisplayName("TC-CPB-09: SensorReading carries severity from AlertEvent")
    void build_sensorReadingCarriesSeverity() {
        List<AlertEvent> events = List.of(
                makeEvent("NOISE", "s3", 90.0, BASE, "B-01", "t1", "CRITICAL")
        );

        CorrelatedPayload payload = builder.build(events, 0.9);

        assertThat(payload.sensors()).hasSize(1);
        assertThat(payload.sensors().get(0).severity()).isEqualTo("CRITICAL");
        assertThat(payload.sensors().get(0).measureType()).isEqualTo("NOISE");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AlertEvent makeEvent(String measureType, String sensorId, double value,
                                 Instant detectedAt, String buildingId,
                                 String tenantId, String severity) {
        AlertEvent e = new AlertEvent();
        e.setSensorId(sensorId);
        e.setMeasureType(measureType);
        e.setValue(value);
        e.setThreshold(50.0);
        e.setSeverity(severity);
        e.setDetectedAt(detectedAt);
        e.setBuildingId(buildingId);
        e.setTenantId(tenantId);
        e.setModule("test");
        return e;
    }
}
