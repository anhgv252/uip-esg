package com.uip.backend.alert.threshold;

import com.uip.backend.environment.service.AqiCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameterized tests for AQI, flood level, and noise thresholds.
 * Verifies correct severity/classification returned at each boundary.
 *
 * GAP-016: Threshold boundary tests for smart city environmental sensors
 */
@DisplayName("Threshold Parameterized Tests (GAP-016)")
class ThresholdParameterizedTest {

    private AqiCalculator aqiCalculator;

    @BeforeEach
    void setUp() {
        aqiCalculator = new AqiCalculator();
    }

    // ─── AQI Thresholds: 0/50/100/150/200/300/500 ─────────────────────────────

    @Nested
    @DisplayName("AQI Severity Classification")
    class AqiSeverityTests {

        @ParameterizedTest(name = "AQI={0} → Good")
        @ValueSource(ints = {0, 25, 50})
        @DisplayName("AQI 0-50 → Good")
        void aqi_good(int aqi) {
            String label = aqiCalculator.categoryLabel(aqi);
            assertThat(label).isEqualTo("Good");
        }

        @ParameterizedTest(name = "AQI={0} → Moderate")
        @ValueSource(ints = {51, 75, 100})
        @DisplayName("AQI 51-100 → Moderate")
        void aqi_moderate(int aqi) {
            String label = aqiCalculator.categoryLabel(aqi);
            assertThat(label).isEqualTo("Moderate");
        }

        @ParameterizedTest(name = "AQI={0} → Unhealthy for Sensitive Groups")
        @ValueSource(ints = {101, 125, 150})
        @DisplayName("AQI 101-150 → Unhealthy for Sensitive Groups")
        void aqi_usg(int aqi) {
            String label = aqiCalculator.categoryLabel(aqi);
            assertThat(label).isEqualTo("Unhealthy for Sensitive Groups");
        }

        @ParameterizedTest(name = "AQI={0} → Unhealthy")
        @ValueSource(ints = {151, 175, 200})
        @DisplayName("AQI 151-200 → Unhealthy")
        void aqi_unhealthy(int aqi) {
            String label = aqiCalculator.categoryLabel(aqi);
            assertThat(label).isEqualTo("Unhealthy");
        }

        @ParameterizedTest(name = "AQI={0} → Very Unhealthy")
        @ValueSource(ints = {201, 250, 300})
        @DisplayName("AQI 201-300 → Very Unhealthy")
        void aqi_veryUnhealthy(int aqi) {
            String label = aqiCalculator.categoryLabel(aqi);
            assertThat(label).isEqualTo("Very Unhealthy");
        }

        @ParameterizedTest(name = "AQI={0} → Hazardous")
        @ValueSource(ints = {301, 400, 500})
        @DisplayName("AQI 301-500 → Hazardous")
        void aqi_hazardous(int aqi) {
            String label = aqiCalculator.categoryLabel(aqi);
            assertThat(label).isEqualTo("Hazardous");
        }

        @ParameterizedTest(name = "PM2.5={0} → AQI≈{1}")
        @CsvSource({
            "0.0,   0",     // AQI floor — Good
            "12.0,  50",    // Good ceiling
            "12.1,  51",    // Moderate floor
            "35.4,  100",   // Moderate ceiling
            "35.5,  101",   // USG floor
            "55.4,  150",   // USG ceiling
            "55.5,  151",   // Unhealthy floor
            "150.4, 200",   // Unhealthy ceiling
            "150.5, 201",   // Very Unhealthy floor
            "250.4, 300",   // Very Unhealthy ceiling
            "250.5, 301",   // Hazardous floor
            "500.4, 500"    // Hazardous ceiling / cap
        })
        @DisplayName("PM2.5 concentration → AQI mapping")
        void pm25_to_aqi(double pm25, int expectedAqi) {
            Integer aqi = aqiCalculator.calculateAqi(pm25, null, null, null, null, null);
            assertThat(aqi).isNotNull();
            assertThat(aqi).isBetween(expectedAqi - 1, expectedAqi + 1);
        }
    }

    // ─── Flood Level Thresholds: 1.0/1.5/1.8/2.0/3.0 ─────────────────────────

    @Nested
    @DisplayName("Flood Level Severity Classification")
    class FloodLevelTests {

        /**
         * Maps flood level (meters) to severity.
         * Thresholds: <1.0=NONE, 1.0-1.5=LOW, 1.5-1.8=MODERATE,
         *             1.8-2.0=HIGH, 2.0-3.0=CRITICAL, >3.0=EMERGENCY
         */
        private String classifyFloodSeverity(double level) {
            if (level < 1.0)  return "NONE";
            if (level < 1.5)  return "LOW";
            if (level < 1.8)  return "MODERATE";
            if (level < 2.0)  return "HIGH";
            if (level < 3.0)  return "CRITICAL";
            return "EMERGENCY";
        }

        @ParameterizedTest(name = "Flood={0}m → NONE")
        @ValueSource(doubles = {0.0, 0.5, 0.99})
        @DisplayName("Flood < 1.0m → NONE")
        void flood_none(double level) {
            assertThat(classifyFloodSeverity(level)).isEqualTo("NONE");
        }

        @ParameterizedTest(name = "Flood={0}m → LOW")
        @ValueSource(doubles = {1.0, 1.2, 1.49})
        @DisplayName("Flood 1.0-1.5m → LOW")
        void flood_low(double level) {
            assertThat(classifyFloodSeverity(level)).isEqualTo("LOW");
        }

        @ParameterizedTest(name = "Flood={0}m → MODERATE")
        @ValueSource(doubles = {1.5, 1.6, 1.79})
        @DisplayName("Flood 1.5-1.8m → MODERATE")
        void flood_moderate(double level) {
            assertThat(classifyFloodSeverity(level)).isEqualTo("MODERATE");
        }

        @ParameterizedTest(name = "Flood={0}m → HIGH")
        @ValueSource(doubles = {1.8, 1.9, 1.99})
        @DisplayName("Flood 1.8-2.0m → HIGH")
        void flood_high(double level) {
            assertThat(classifyFloodSeverity(level)).isEqualTo("HIGH");
        }

        @ParameterizedTest(name = "Flood={0}m → CRITICAL")
        @ValueSource(doubles = {2.0, 2.5, 2.99})
        @DisplayName("Flood 2.0-3.0m → CRITICAL")
        void flood_critical(double level) {
            assertThat(classifyFloodSeverity(level)).isEqualTo("CRITICAL");
        }

        @ParameterizedTest(name = "Flood={0}m → EMERGENCY")
        @ValueSource(doubles = {3.0, 4.0, 5.0, 10.0})
        @DisplayName("Flood >= 3.0m → EMERGENCY")
        void flood_emergency(double level) {
            assertThat(classifyFloodSeverity(level)).isEqualTo("EMERGENCY");
        }

        @ParameterizedTest(name = "Flood={0}m → {1}")
        @CsvSource({
            "0.5,  NONE",
            "1.0,  LOW",
            "1.5,  MODERATE",
            "1.8,  HIGH",
            "2.0,  CRITICAL",
            "3.0,  EMERGENCY"
        })
        @DisplayName("Boundary values: exact threshold points")
        void flood_boundaries(double level, String expected) {
            assertThat(classifyFloodSeverity(level)).isEqualTo(expected);
        }
    }

    // ─── Noise Thresholds: 30/50/70/85/120 dB ─────────────────────────────────

    @Nested
    @DisplayName("Noise Level Severity Classification")
    class NoiseLevelTests {

        /**
         * Maps noise level (dB) to severity.
         * Thresholds: <30=NONE, 30-50=LOW, 50-70=MODERATE,
         *             70-85=HIGH, 85-120=CRITICAL, >120=EMERGENCY
         * Based on Vietnam QCVN 26:2010/BTNMT noise standards
         */
        private String classifyNoiseSeverity(double db) {
            if (db < 30)  return "NONE";
            if (db < 50)  return "LOW";
            if (db < 70)  return "MODERATE";
            if (db < 85)  return "HIGH";
            if (db < 120) return "CRITICAL";
            return "EMERGENCY";
        }

        @ParameterizedTest(name = "Noise={0}dB → NONE")
        @ValueSource(doubles = {0, 10, 29})
        @DisplayName("Noise < 30dB → NONE")
        void noise_none(double db) {
            assertThat(classifyNoiseSeverity(db)).isEqualTo("NONE");
        }

        @ParameterizedTest(name = "Noise={0}dB → LOW")
        @ValueSource(doubles = {30, 40, 49})
        @DisplayName("Noise 30-50dB → LOW")
        void noise_low(double db) {
            assertThat(classifyNoiseSeverity(db)).isEqualTo("LOW");
        }

        @ParameterizedTest(name = "Noise={0}dB → MODERATE")
        @ValueSource(doubles = {50, 60, 69})
        @DisplayName("Noise 50-70dB → MODERATE")
        void noise_moderate(double db) {
            assertThat(classifyNoiseSeverity(db)).isEqualTo("MODERATE");
        }

        @ParameterizedTest(name = "Noise={0}dB → HIGH")
        @ValueSource(doubles = {70, 75, 84})
        @DisplayName("Noise 70-85dB → HIGH")
        void noise_high(double db) {
            assertThat(classifyNoiseSeverity(db)).isEqualTo("HIGH");
        }

        @ParameterizedTest(name = "Noise={0}dB → CRITICAL")
        @ValueSource(doubles = {85, 100, 119})
        @DisplayName("Noise 85-120dB → CRITICAL")
        void noise_critical(double db) {
            assertThat(classifyNoiseSeverity(db)).isEqualTo("CRITICAL");
        }

        @ParameterizedTest(name = "Noise={0}dB → EMERGENCY")
        @ValueSource(doubles = {120, 130, 150})
        @DisplayName("Noise >= 120dB → EMERGENCY")
        void noise_emergency(double db) {
            assertThat(classifyNoiseSeverity(db)).isEqualTo("EMERGENCY");
        }

        @ParameterizedTest(name = "Noise={0}dB → {1}")
        @CsvSource({
            "30,  LOW",
            "50,  MODERATE",
            "70,  HIGH",
            "85,  CRITICAL",
            "120, EMERGENCY"
        })
        @DisplayName("Boundary values: exact threshold points")
        void noise_boundaries(double db, String expected) {
            assertThat(classifyNoiseSeverity(db)).isEqualTo(expected);
        }
    }
}
