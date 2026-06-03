package com.uip.backend.safety;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.safety.model.SafetyScore;
import com.uip.backend.safety.service.BuildingSafetyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BuildingSafetyService.computeScore() — pure algorithm, no Spring context.
 *
 * <p>Tests the score formula: 100 - (CRITICAL×30) - (WARNING×10), clamped [0,100].</p>
 */
@DisplayName("BuildingSafetyService — score algorithm")
class BuildingSafetyServiceTest {

    private static final String BUILDING_ID = "BLDG-001";

    // ─── No alerts ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("No open alerts → score=100, status=SAFE, activeAlerts=0")
    void noAlerts_perfectScore() {
        SafetyScore score = BuildingSafetyService.computeScore(BUILDING_ID, Collections.emptyList());

        assertThat(score.score()).isEqualTo(100);
        assertThat(score.status()).isEqualTo("SAFE");
        assertThat(score.activeAlerts()).isEqualTo(0);
        assertThat(score.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(score.lastUpdated()).isNotNull();
    }

    // ─── Single alert ────────────────────────────────────────────────────────

    @Test
    @DisplayName("1 WARNING → score=90, status=WARNING")
    void oneWarning_score90() {
        List<AlertEvent> alerts = List.of(alertEvent("WARNING"));
        SafetyScore score = BuildingSafetyService.computeScore(BUILDING_ID, alerts);

        assertThat(score.score()).isEqualTo(90);
        assertThat(score.status()).isEqualTo("WARNING");
        assertThat(score.activeAlerts()).isEqualTo(1);
    }

    @Test
    @DisplayName("1 CRITICAL → score=70, status=CRITICAL")
    void oneCritical_score70() {
        List<AlertEvent> alerts = List.of(alertEvent("CRITICAL"));
        SafetyScore score = BuildingSafetyService.computeScore(BUILDING_ID, alerts);

        assertThat(score.score()).isEqualTo(70);
        assertThat(score.status()).isEqualTo("CRITICAL");
        assertThat(score.activeAlerts()).isEqualTo(1);
    }

    // ─── Multiple alerts ─────────────────────────────────────────────────────

    @ParameterizedTest(name = "CRITICAL={0}, WARNING={1} → score={2}, status={3}")
    @CsvSource({
            "0, 0,  100, SAFE",
            "0, 1,   90, WARNING",
            "0, 3,   70, WARNING",
            "1, 0,   70, CRITICAL",
            "1, 2,   50, CRITICAL",
            "2, 1,   30, CRITICAL",
            "3, 1,    0, CRITICAL",   // 100 - 90 - 10 = 0 (exact floor)
            "4, 0,    0, CRITICAL",   // 100 - 120 = -20 → clamped to 0
            "5, 5,    0, CRITICAL",   // deeply negative → 0
    })
    @DisplayName("Score formula: 100 - CRITICAL×30 - WARNING×10, clamped [0,100]")
    void scoreFormula(int criticalCount, int warningCount, int expectedScore, String expectedStatus) {
        List<AlertEvent> alerts = buildAlertList(criticalCount, warningCount);
        SafetyScore score = BuildingSafetyService.computeScore(BUILDING_ID, alerts);

        assertThat(score.score()).isEqualTo(expectedScore);
        assertThat(score.status()).isEqualTo(expectedStatus);
        assertThat(score.activeAlerts()).isEqualTo(criticalCount + warningCount);
    }

    // ─── Status priority: CRITICAL > WARNING > SAFE ──────────────────────────

    @Test
    @DisplayName("Mixed CRITICAL + WARNING → status=CRITICAL (highest severity wins)")
    void mixed_criticalDominates() {
        List<AlertEvent> alerts = List.of(alertEvent("WARNING"), alertEvent("CRITICAL"), alertEvent("WARNING"));
        SafetyScore score = BuildingSafetyService.computeScore(BUILDING_ID, alerts);

        assertThat(score.status()).isEqualTo("CRITICAL");
        assertThat(score.score()).isEqualTo(100 - 30 - 20); // 50
    }

    // ─── SafetyScore.offline ─────────────────────────────────────────────────

    @Test
    @DisplayName("offline() factory → score=0, status=OFFLINE")
    void offline_factory() {
        SafetyScore score = SafetyScore.offline(BUILDING_ID);

        assertThat(score.score()).isEqualTo(0);
        assertThat(score.status()).isEqualTo("OFFLINE");
        assertThat(score.activeAlerts()).isEqualTo(0);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static AlertEvent alertEvent(String severity) {
        AlertEvent e = new AlertEvent();
        e.setSeverity(severity);
        e.setModule("STRUCTURAL");
        e.setStatus("OPEN");
        e.setDetectedAt(Instant.now());
        e.setSensorId("SENSOR-TEST");
        e.setMeasureType("VIBRATION");
        e.setValue(15.0);
        e.setThreshold(10.0);
        return e;
    }

    private static List<AlertEvent> buildAlertList(int criticalCount, int warningCount) {
        List<AlertEvent> alerts = new java.util.ArrayList<>();
        for (int i = 0; i < criticalCount; i++) alerts.add(alertEvent("CRITICAL"));
        for (int i = 0; i < warningCount; i++)  alerts.add(alertEvent("WARNING"));
        return alerts;
    }
}
