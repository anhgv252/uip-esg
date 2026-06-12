package com.uip.backend.ai.anomaly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WelfordAnomalyDetector — unit tests")
class WelfordAnomalyDetectorTest {

    private WelfordAnomalyDetector detector;

    /** Feed {@code n} readings of the given value into the detector for the given key. */
    private void feedN(String key, double value, int n) {
        for (int i = 0; i < n; i++) {
            detector.update(key, value);
        }
    }

    /** Feed 100 normally distributed readings to complete the learning phase. */
    private void completeLearning(String key) {
        // Alternate between 50 and 55 to give a small but non-zero std dev
        for (int i = 0; i < 100; i++) {
            detector.update(key, i % 2 == 0 ? 50.0 : 55.0);
        }
    }

    @BeforeEach
    void setUp() {
        detector = new WelfordAnomalyDetector();
        // Inject config values (normally bound by Spring @Value)
        ReflectionTestUtils.setField(detector, "learningPhaseCount", 100);
        ReflectionTestUtils.setField(detector, "sigmaThreshold", 3.0);
    }

    // ─── Learning phase ───────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-WAD-01: First 99 readings → still in learning phase, no anomaly")
    void learningPhase_first99_noAnomaly() {
        String key = "AQI:b1";
        for (int i = 0; i < 99; i++) {
            detector.update(key, 50.0);
        }
        assertThat(detector.isInLearningPhase(key)).isTrue();
        assertThat(detector.isAnomaly(key, 50.0)).isFalse();
    }

    @Test
    @DisplayName("TC-WAD-02: Exactly 100 readings → exits learning phase")
    void learningPhase_exactly100_exitPhase() {
        String key = "AQI:b1";
        feedN(key, 50.0, 100);
        assertThat(detector.isInLearningPhase(key)).isFalse();
    }

    @Test
    @DisplayName("TC-WAD-03: New sensor key → isInLearningPhase=true, isAnomaly=false")
    void unknownKey_inLearningPhase() {
        assertThat(detector.isInLearningPhase("UNKNOWN:x")).isTrue();
        assertThat(detector.isAnomaly("UNKNOWN:x", 9999.0)).isFalse();
    }

    // ─── Normal value after learning ──────────────────────────────────────────

    @Test
    @DisplayName("TC-WAD-04: After learning, value within 1-sigma → not an anomaly")
    void afterLearning_normalValue_noAnomaly() {
        String key = "AQI:b2";
        completeLearning(key);
        // Mean ≈ 52.5, stdDev ≈ 2.5 — value 53 is well within 3-sigma
        assertThat(detector.isAnomaly(key, 53.0)).isFalse();
    }

    // ─── 4-sigma spike ────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-WAD-05: After learning, 4-sigma spike → anomaly detected")
    void afterLearning_fourSigmaSpike_isAnomaly() {
        String key = "AQI:b3";
        completeLearning(key);
        // Mean≈52.5, stdDev≈2.5 → 4-sigma = 52.5 + 4*2.5 = 62.5
        assertThat(detector.isAnomaly(key, 65.0)).isTrue();
    }

    // ─── Sensor-type-specific tests ───────────────────────────────────────────

    @Test
    @DisplayName("TC-WAD-06: AQI sensor — update + isAnomaly")
    void aqiSensor_updateAndAnomaly() {
        String key = "AQI:district1";
        completeLearning(key);
        // Normal AQI reading
        assertThat(detector.isAnomaly(key, 52.0)).isFalse();
        // Extreme spike (far above 3-sigma)
        assertThat(detector.isAnomaly(key, 200.0)).isTrue();
    }

    @Test
    @DisplayName("TC-WAD-07: WATER_LEVEL sensor — update + isAnomaly")
    void waterLevelSensor_updateAndAnomaly() {
        String key = "WATER_LEVEL:pump-station-01";
        completeLearning(key);
        assertThat(detector.isAnomaly(key, 52.0)).isFalse();
        assertThat(detector.isAnomaly(key, 200.0)).isTrue();
    }

    @Test
    @DisplayName("TC-WAD-08: NOISE sensor — update + isAnomaly")
    void noiseSensor_updateAndAnomaly() {
        String key = "NOISE:zone-7";
        completeLearning(key);
        assertThat(detector.isAnomaly(key, 52.0)).isFalse();
        assertThat(detector.isAnomaly(key, 200.0)).isTrue();
    }

    @Test
    @DisplayName("TC-WAD-09: HUMIDITY sensor — update + isAnomaly")
    void humiditySensor_updateAndAnomaly() {
        String key = "HUMIDITY:floor-3";
        completeLearning(key);
        assertThat(detector.isAnomaly(key, 52.0)).isFalse();
        assertThat(detector.isAnomaly(key, 200.0)).isTrue();
    }

    // ─── Multiple sensors isolation ───────────────────────────────────────────

    @Test
    @DisplayName("TC-WAD-10: Multiple sensors do not interfere with each other")
    void multipleSensors_doNotInterfere() {
        String keyA = "AQI:building-a";
        String keyB = "WATER_LEVEL:building-b";

        // Build up learning phase for keyA only with very small variance (all = 50.0)
        feedN(keyA, 50.0, 100);

        // keyB still in learning phase — should not be anomalous
        detector.update(keyB, 9999.0);
        assertThat(detector.isAnomaly(keyB, 9999.0)).isFalse();

        // keyA stable mean → normal reading not flagged
        assertThat(detector.isAnomaly(keyA, 50.0)).isFalse();
    }

    // ─── WelfordState helpers ─────────────────────────────────────────────────

    @Test
    @DisplayName("TC-WAD-11: WelfordState.empty() has count=0")
    void welfordStateEmpty_hasZeroCount() {
        WelfordAnomalyDetector.WelfordState state = WelfordAnomalyDetector.WelfordState.empty();
        assertThat(state.count()).isZero();
        assertThat(state.stdDev()).isZero();
    }

    @Test
    @DisplayName("TC-WAD-12: Single-reading state → stdDev=0 (Bessel corrected)")
    void singleReading_stdDevIsZero() {
        String key = "TEMPERATURE:sensor-1";
        WelfordAnomalyDetector.WelfordState state = detector.update(key, 100.0);
        assertThat(state.stdDev()).isZero();
    }
}
