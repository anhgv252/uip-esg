package com.uip.backend.ai.anomaly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4-AI-07: Extended unit tests for WelfordAnomalyDetector covering
 * VIBRATION, SMOKE sensor types and the cold-start learning phase transition.
 */
@DisplayName("WelfordAnomalyDetector — extended sensor type tests (M4-AI-07)")
class WelfordAnomalyDetectorExtendedTest {

    private WelfordAnomalyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new WelfordAnomalyDetector();
        ReflectionTestUtils.setField(detector, "learningPhaseCount", 100);
        ReflectionTestUtils.setField(detector, "sigmaThreshold", 3.0);
    }

    /** Feeds {@code n} alternating readings to build stable statistics. */
    private void completeLearning(String key) {
        for (int i = 0; i < 100; i++) {
            detector.update(key, i % 2 == 0 ? 50.0 : 55.0);
        }
    }

    // ── VIBRATION sensor ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("VIBRATION sensor type")
    class VibrationSensorTests {

        @Test
        @DisplayName("TC-WAD-EXT-01: VIBRATION sensor — first 99 readings still in learning phase")
        void vibration_learningPhase_first99() {
            String key = "VIBRATION:column-a1";
            for (int i = 0; i < 99; i++) {
                detector.update(key, 0.5);
            }
            assertThat(detector.isInLearningPhase(key)).isTrue();
            assertThat(detector.isAnomaly(key, 0.5)).isFalse();
        }

        @Test
        @DisplayName("TC-WAD-EXT-02: VIBRATION sensor — exits learning at 100 readings")
        void vibration_exits_learningPhase_at100() {
            String key = "VIBRATION:column-a1";
            completeLearning(key);
            assertThat(detector.isInLearningPhase(key)).isFalse();
        }

        @Test
        @DisplayName("TC-WAD-EXT-03: VIBRATION sensor — normal reading not flagged after learning")
        void vibration_normalReading_notAnomaly() {
            String key = "VIBRATION:floor-02";
            completeLearning(key);
            // Mean ≈ 52.5, stdDev ≈ 2.5 — value within range
            assertThat(detector.isAnomaly(key, 53.0)).isFalse();
        }

        @Test
        @DisplayName("TC-WAD-EXT-04: VIBRATION sensor — extreme seismic spike flagged as anomaly")
        void vibration_extremeSpike_isAnomaly() {
            String key = "VIBRATION:seismic-zone-1";
            completeLearning(key);
            // Very far above 3-sigma → anomaly
            assertThat(detector.isAnomaly(key, 500.0)).isTrue();
        }

        @Test
        @DisplayName("TC-WAD-EXT-05: VIBRATION sensor key format VIBRATION:{location} is valid SensorType enum")
        void vibration_enumValueExists() {
            // Verify the SensorType enum has VIBRATION
            WelfordAnomalyDetector.SensorType vib =
                    WelfordAnomalyDetector.SensorType.VIBRATION;
            assertThat(vib).isNotNull();
            assertThat(vib.name()).isEqualTo("VIBRATION");
        }
    }

    // ── SMOKE sensor ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SMOKE sensor type")
    class SmokeSensorTests {

        @Test
        @DisplayName("TC-WAD-EXT-06: SMOKE sensor — in learning phase initially")
        void smoke_inLearningPhase_initially() {
            String key = "SMOKE:stairwell-3f";
            assertThat(detector.isInLearningPhase(key)).isTrue();
        }

        @Test
        @DisplayName("TC-WAD-EXT-07: SMOKE sensor — baseline reading not flagged after learning")
        void smoke_baselineReading_notAnomaly() {
            String key = "SMOKE:corridor-b";
            completeLearning(key);
            assertThat(detector.isAnomaly(key, 52.0)).isFalse();
        }

        @Test
        @DisplayName("TC-WAD-EXT-08: SMOKE sensor — fire-level density spike flagged as anomaly")
        void smoke_fireSpike_isAnomaly() {
            String key = "SMOKE:server-room";
            completeLearning(key);
            // Extreme reading far beyond 3-sigma
            assertThat(detector.isAnomaly(key, 1000.0)).isTrue();
        }

        @Test
        @DisplayName("TC-WAD-EXT-09: SMOKE enum value exists in SensorType")
        void smoke_enumValueExists() {
            WelfordAnomalyDetector.SensorType smoke =
                    WelfordAnomalyDetector.SensorType.SMOKE;
            assertThat(smoke).isNotNull();
            assertThat(smoke.name()).isEqualTo("SMOKE");
        }
    }

    // ── New sensor types: PRESSURE, CO_LEVEL ─────────────────────────────────

    @Nested
    @DisplayName("Additional new sensor types (PRESSURE, CO_LEVEL)")
    class NewSensorTypesTests {

        @Test
        @DisplayName("TC-WAD-EXT-10: PRESSURE enum value exists")
        void pressure_enumValueExists() {
            WelfordAnomalyDetector.SensorType pressure =
                    WelfordAnomalyDetector.SensorType.PRESSURE;
            assertThat(pressure.name()).isEqualTo("PRESSURE");
        }

        @Test
        @DisplayName("TC-WAD-EXT-11: CO_LEVEL enum value exists")
        void coLevel_enumValueExists() {
            WelfordAnomalyDetector.SensorType co =
                    WelfordAnomalyDetector.SensorType.CO_LEVEL;
            assertThat(co.name()).isEqualTo("CO_LEVEL");
        }
    }

    // ── Cold-start learning phase transition ──────────────────────────────────

    @Nested
    @DisplayName("Cold-start: learning phase clears after learningPhaseSize readings")
    class ColdStartLearningPhaseTests {

        @Test
        @DisplayName("TC-WAD-EXT-12: cold-start flag true before learningPhaseSize readings")
        void coldStart_flagTrue_beforeThreshold() {
            String key = "VIBRATION:cold-start-test";
            // Feed 99 readings (one below threshold)
            for (int i = 0; i < 99; i++) {
                detector.update(key, 42.0);
            }
            assertThat(detector.isInLearningPhase(key)).isTrue();
        }

        @Test
        @DisplayName("TC-WAD-EXT-13: cold-start flag clears exactly at learningPhaseSize (100th reading)")
        void coldStart_flagClears_atThreshold() {
            String key = "SMOKE:cold-start-test";
            // Feed exactly 100 readings
            for (int i = 0; i < 100; i++) {
                detector.update(key, 30.0 + (i % 2 == 0 ? 0 : 5));
            }
            assertThat(detector.isInLearningPhase(key)).isFalse();
        }

        @Test
        @DisplayName("TC-WAD-EXT-14: after cold-start clears, anomaly detection activates for extreme values")
        void coldStart_afterClear_anomalyDetectionActive() {
            String key = "VIBRATION:post-coldstart";
            // Build stable baseline of 100 readings
            for (int i = 0; i < 100; i++) {
                detector.update(key, i % 2 == 0 ? 10.0 : 12.0);
            }
            // Normal value → not anomaly
            assertThat(detector.isAnomaly(key, 11.0)).isFalse();
            // Extreme value → anomaly
            assertThat(detector.isAnomaly(key, 999.0)).isTrue();
        }
    }

    // ── WelfordConfig properties ──────────────────────────────────────────────

    @Nested
    @DisplayName("WelfordConfig defaults")
    class WelfordConfigTests {

        @Test
        @DisplayName("TC-WAD-EXT-15: WelfordConfig has correct defaults")
        void welfordConfig_correctDefaults() {
            WelfordConfig config = new WelfordConfig();
            assertThat(config.getLearningPhaseSize()).isEqualTo(100);
            assertThat(config.getSensitivityMultiplier()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("TC-WAD-EXT-16: WelfordConfig setters work")
        void welfordConfig_settersWork() {
            WelfordConfig config = new WelfordConfig();
            config.setLearningPhaseSize(200);
            config.setSensitivityMultiplier(2.5);
            assertThat(config.getLearningPhaseSize()).isEqualTo(200);
            assertThat(config.getSensitivityMultiplier()).isEqualTo(2.5);
        }
    }
}
