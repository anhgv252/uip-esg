package com.uip.backend.ai.anomaly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link BaselineDriftDetector} — M4-COR-05.
 * No Spring context; plain instantiation.
 */
@DisplayName("BaselineDriftDetector — unit tests")
class BaselineDriftDetectorTest {

    private BaselineDriftDetector detector;

    private static final Instant NOW = Instant.parse("2026-06-12T10:00:00Z");

    @BeforeEach
    void setUp() {
        detector = new BaselineDriftDetector();
    }

    // ─── TC-BDD-01: No drift when values are stable ───────────────────────────

    @Test
    @DisplayName("TC-BDD-01: Stable readings — no drift detected")
    void stableReadings_noDrift() {
        String sensor = "AQI:b1";
        double baseline = 50.0;

        // Feed readings at or below the baseline
        for (int i = 0; i < 10; i++) {
            detector.addReading(sensor, 48.0 + i * 0.1, NOW.minus(i, ChronoUnit.HOURS));
        }

        // 7-day mean ≈ 48.45, baseline=50 → no drift (mean < baseline*1.10=55)
        Optional<BaselineDriftDetector.DriftEvent> result = detector.checkDrift(sensor, baseline);
        assertThat(result).isEmpty();
    }

    // ─── TC-BDD-02: 10% drift detected ───────────────────────────────────────

    @Test
    @DisplayName("TC-BDD-02: Readings 15% above baseline → drift detected")
    void readingsAboveThreshold_driftDetected() {
        String sensor = "AQI:b2";
        double baseline = 50.0;
        double elevatedValue = 58.0; // 16% above baseline

        for (int i = 0; i < 5; i++) {
            detector.addReading(sensor, elevatedValue, NOW.minus(i, ChronoUnit.HOURS));
        }

        Optional<BaselineDriftDetector.DriftEvent> result = detector.checkDrift(sensor, baseline);
        assertThat(result).isPresent();
        assertThat(result.get().sensorId()).isEqualTo(sensor);
        assertThat(result.get().oldBaseline()).isEqualTo(baseline);
        assertThat(result.get().newBaseline()).isCloseTo(elevatedValue, within(0.01));
        assertThat(result.get().driftPercent()).isGreaterThan(0.10);
    }

    // ─── TC-BDD-03: Exactly 10% drift is NOT above threshold ─────────────────

    @Test
    @DisplayName("TC-BDD-03: Readings exactly at 10% boundary → no drift (strict >)")
    void exactlyAtThreshold_noDrift() {
        String sensor = "AQI:b3";
        double baseline = 100.0;
        double exactlyAt = 110.0; // exactly 10% — threshold is baseline * 1.10 = 110

        for (int i = 0; i < 5; i++) {
            detector.addReading(sensor, exactlyAt, NOW.minus(i, ChronoUnit.HOURS));
        }

        // mean = 110.0 = baseline * 1.10 → NOT strictly greater → no drift
        Optional<BaselineDriftDetector.DriftEvent> result = detector.checkDrift(sensor, baseline);
        assertThat(result).isEmpty();
    }

    // ─── TC-BDD-04: Threshold auto-adjustment ────────────────────────────────

    @Test
    @DisplayName("TC-BDD-04: adjustThreshold multiplies original by (1 + driftPercent)")
    void adjustThreshold_computedCorrectly() {
        double originalThreshold = 200.0;
        double driftPercent      = 0.15; // 15%

        double adjusted = detector.adjustThreshold(originalThreshold, driftPercent);

        assertThat(adjusted).isCloseTo(230.0, within(0.001));
    }

    @Test
    @DisplayName("TC-BDD-05: adjustThreshold with 0% drift returns original threshold")
    void adjustThreshold_zeroDrift_returnsOriginal() {
        assertThat(detector.adjustThreshold(150.0, 0.0)).isCloseTo(150.0, within(1e-9));
    }

    // ─── TC-BDD-06: 7-day window evicts old entries ───────────────────────────

    @Test
    @DisplayName("TC-BDD-06: Readings older than 7 days are evicted from window")
    void oldEntries_evictedFromWindow() {
        String sensor = "AQI:b4";

        // Add an old reading (8 days ago) — should be evicted
        Instant eightDaysAgo = NOW.minus(8, ChronoUnit.DAYS);
        detector.addReading(sensor, 999.0, eightDaysAgo);

        // Add a recent reading
        detector.addReading(sensor, 50.0, NOW);

        // The old 999.0 entry should have been evicted by the second addReading
        int size = detector.windowSize(sensor);
        assertThat(size).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-BDD-07: Evicted old entries do not influence drift calculation")
    void evictedEntries_doNotInfluenceDrift() {
        String sensor = "AQI:b5";
        double baseline = 50.0;

        // Add a very high reading 8 days ago (outside window)
        detector.addReading(sensor, 500.0, NOW.minus(8, ChronoUnit.DAYS));

        // Add normal readings within the window (will evict the old one on next call)
        for (int i = 0; i < 5; i++) {
            detector.addReading(sensor, 48.0, NOW.minus(i, ChronoUnit.HOURS));
        }

        // Old 500.0 entry is gone — drift should NOT be detected
        Optional<BaselineDriftDetector.DriftEvent> result = detector.checkDrift(sensor, baseline);
        assertThat(result).isEmpty();
    }

    // ─── TC-BDD-08: No readings → no drift ───────────────────────────────────

    @Test
    @DisplayName("TC-BDD-08: Unknown sensor returns empty drift")
    void unknownSensor_returnsEmpty() {
        Optional<BaselineDriftDetector.DriftEvent> result =
                detector.checkDrift("UNKNOWN:sensor", 50.0);
        assertThat(result).isEmpty();
    }

    // ─── TC-BDD-09: Multiple sensors are independent ─────────────────────────

    @Test
    @DisplayName("TC-BDD-09: Drift in one sensor does not affect another sensor")
    void multipleSensors_areIndependent() {
        String sensorA = "AQI:a1";
        String sensorB = "AQI:b1";
        double baseline = 50.0;

        // SensorA has drift (60.0 > 50*1.10=55)
        for (int i = 0; i < 5; i++) {
            detector.addReading(sensorA, 60.0, NOW.minus(i, ChronoUnit.HOURS));
        }
        // SensorB is stable
        for (int i = 0; i < 5; i++) {
            detector.addReading(sensorB, 49.0, NOW.minus(i, ChronoUnit.HOURS));
        }

        assertThat(detector.checkDrift(sensorA, baseline)).isPresent();
        assertThat(detector.checkDrift(sensorB, baseline)).isEmpty();
    }

    // ─── TC-BDD-10: windowSize returns correct count ─────────────────────────

    @Test
    @DisplayName("TC-BDD-10: windowSize returns 0 for unknown sensor")
    void windowSize_unknownSensor_returnsZero() {
        assertThat(detector.windowSize("NOT:EXISTS")).isZero();
    }

    @Test
    @DisplayName("TC-BDD-11: windowSize reflects number of in-window entries")
    void windowSize_afterAdding_correctCount() {
        String sensor = "NOISE:z1";
        for (int i = 0; i < 3; i++) {
            detector.addReading(sensor, 70.0 + i, NOW.minus(i, ChronoUnit.HOURS));
        }
        assertThat(detector.windowSize(sensor)).isEqualTo(3);
    }
}
