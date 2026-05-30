package com.uip.flink.flood;

import com.uip.flink.common.NgsiLdMessage;
import com.uip.flink.common.NgsiLdMessage.NgsiLdProperty;
import com.uip.flink.common.NgsiLdMessage.Meta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Flood Alert — threshold classification + pattern detection logic.
 *
 * 15 tests covering:
 *   - ThresholdCondition boundary tests (6)
 *   - FloodAlertJob.isAboveP2Threshold (3)
 *   - Severity mapping (3)
 *   - FloodAlertEvent creation (3)
 */
@DisplayName("Flood Alert — Threshold & Detection")
class FloodAlertJobTest {

    // --- ThresholdCondition tests ---

    @Nested
    @DisplayName("ThresholdCondition — boundary tests")
    class ThresholdConditionTests {

        @Test
        @DisplayName("RAINFALL just below P2 (49.9) → no alert")
        void rainfall_belowP2_noAlert() {
            String severity = ThresholdCondition.classifySeverity("RAINFALL", 49.9);
            assertThat(severity).isNull();
        }

        @Test
        @DisplayName("RAINFALL exactly P2 (50) → P2_ADVISORY")
        void rainfall_exactP2_advisory() {
            String severity = ThresholdCondition.classifySeverity("RAINFALL", 50.0);
            assertThat(severity).isEqualTo("P2_ADVISORY");
        }

        @Test
        @DisplayName("RAINFALL between P2-P1 (65) → P2_ADVISORY")
        void rainfall_betweenP2P1_advisory() {
            String severity = ThresholdCondition.classifySeverity("RAINFALL", 65.0);
            assertThat(severity).isEqualTo("P2_ADVISORY");
        }

        @Test
        @DisplayName("RAINFALL exactly P1 (80) → P1_WARNING")
        void rainfall_exactP1_warning() {
            String severity = ThresholdCondition.classifySeverity("RAINFALL", 80.0);
            assertThat(severity).isEqualTo("P1_WARNING");
        }

        @Test
        @DisplayName("RAINFALL between P1-P0 (100) → P1_WARNING")
        void rainfall_betweenP1P0_warning() {
            String severity = ThresholdCondition.classifySeverity("RAINFALL", 100.0);
            assertThat(severity).isEqualTo("P1_WARNING");
        }

        @Test
        @DisplayName("RAINFALL exactly P0 (120) → P0_EMERGENCY")
        void rainfall_exactP0_emergency() {
            String severity = ThresholdCondition.classifySeverity("RAINFALL", 120.0);
            assertThat(severity).isEqualTo("P0_EMERGENCY");
        }

        @Test
        @DisplayName("WATER_LEVEL below P2 (1.9) → no alert")
        void waterLevel_belowP2_noAlert() {
            assertThat(ThresholdCondition.classifySeverity("WATER_LEVEL", 1.9)).isNull();
        }

        @Test
        @DisplayName("WATER_LEVEL P1 range (3.5) → P1_WARNING")
        void waterLevel_p1_warning() {
            assertThat(ThresholdCondition.classifySeverity("WATER_LEVEL", 3.5)).isEqualTo("P1_WARNING");
        }

        @Test
        @DisplayName("WATER_LEVEL P0 range (5.5) → P0_EMERGENCY")
        void waterLevel_p0_emergency() {
            assertThat(ThresholdCondition.classifySeverity("WATER_LEVEL", 5.5)).isEqualTo("P0_EMERGENCY");
        }

        @Test
        @DisplayName("SOIL_MOISTURE below P2 (65) → no alert")
        void soilMoisture_belowP2_noAlert() {
            assertThat(ThresholdCondition.classifySeverity("SOIL_MOISTURE", 65.0)).isNull();
        }

        @Test
        @DisplayName("SOIL_MOISTURE P1 range (87) → P1_WARNING")
        void soilMoisture_p1_warning() {
            assertThat(ThresholdCondition.classifySeverity("SOIL_MOISTURE", 87.0)).isEqualTo("P1_WARNING");
        }

        @Test
        @DisplayName("SOIL_MOISTURE P0 range (96) → P0_EMERGENCY")
        void soilMoisture_p0_emergency() {
            assertThat(ThresholdCondition.classifySeverity("SOIL_MOISTURE", 96.0)).isEqualTo("P0_EMERGENCY");
        }
    }

    // --- isFloodSensor tests ---

    @Nested
    @DisplayName("isFloodSensor — sensor type filtering")
    class FloodSensorFilterTests {

        @Test
        @DisplayName("RAINFALL is flood sensor")
        void rainfall_isFlood() {
            assertThat(ThresholdCondition.isFloodSensor("RAINFALL")).isTrue();
        }

        @Test
        @DisplayName("WATER_LEVEL is flood sensor (case insensitive)")
        void waterLevel_caseInsensitive() {
            assertThat(ThresholdCondition.isFloodSensor("water_level")).isTrue();
        }

        @Test
        @DisplayName("AQI is NOT flood sensor")
        void aqi_notFlood() {
            assertThat(ThresholdCondition.isFloodSensor("AQI")).isFalse();
        }
    }

    // --- isAboveP2Threshold tests ---

    @Nested
    @DisplayName("FloodAlertJob.isAboveP2Threshold — message-level check")
    class AboveP2Tests {

        private NgsiLdMessage msg;

        private NgsiLdMessage createMessage(String sensorType, String measurementKey, double value) {
            NgsiLdMessage m = new NgsiLdMessage();

            NgsiLdProperty<String> deviceId = new NgsiLdProperty<>();
            deviceId.setValue("SENSOR-FLOOD-001");
            m.setDeviceId(deviceId);

            NgsiLdProperty<Long> observedAt = new NgsiLdProperty<>();
            observedAt.setValue(System.currentTimeMillis());
            m.setObservedAt(observedAt);

            NgsiLdProperty<Map<String, Double>> measurements = new NgsiLdProperty<>();
            measurements.setValue(Map.of(measurementKey, value));
            m.setMeasurements(measurements);

            Meta meta = new Meta();
            meta.setSensorType(sensorType);
            meta.setTenantId("hcm");
            meta.setDistrict("district-1");
            m.setMeta(meta);

            return m;
        }

        @Test
        @DisplayName("RAINFALL 60mm/h → above P2 threshold")
        void rainfall60_aboveP2() {
            msg = createMessage("RAINFALL", "rainfall", 60.0);
            assertThat(FloodAlertJob.isAboveP2Threshold(msg)).isTrue();
        }

        @Test
        @DisplayName("RAINFALL 30mm/h → below P2 threshold")
        void rainfall30_belowP2() {
            msg = createMessage("RAINFALL", "rainfall", 30.0);
            assertThat(FloodAlertJob.isAboveP2Threshold(msg)).isFalse();
        }

        @Test
        @DisplayName("null message → false")
        void nullMessage_false() {
            assertThat(FloodAlertJob.isAboveP2Threshold(null)).isFalse();
        }
    }

    // --- FloodAlertEvent creation ---

    @Nested
    @DisplayName("FloodAlertEvent — creation")
    class EventCreationTests {

        @Test
        @DisplayName("Create event with all fields")
        void createEvent_allFields() {
            FloodAlertEvent event = new FloodAlertEvent(
                    "SENSOR-FLOOD-001", "RAINFALL", "hcm",
                    100.0, 80.0, "P1_WARNING", "district-1",
                    System.currentTimeMillis(), 3
            );
            assertThat(event.getSensorId()).isEqualTo("SENSOR-FLOOD-001");
            assertThat(event.getSensorType()).isEqualTo("RAINFALL");
            assertThat(event.getSeverity()).isEqualTo("P1_WARNING");
            assertThat(event.getConsecutiveCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Create event — P0 EMERGENCY")
        void createEvent_p0Emergency() {
            FloodAlertEvent event = new FloodAlertEvent(
                    "SENSOR-FL-002", "WATER_LEVEL", "hcm",
                    5.2, 5.0, "P0_EMERGENCY", "district-7",
                    System.currentTimeMillis(), 4
            );
            assertThat(event.getSeverity()).isEqualTo("P0_EMERGENCY");
            assertThat(event.getValue()).isEqualTo(5.2);
            assertThat(event.getThreshold()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("Create event — default constructor + setters")
        void createEvent_defaultConstructor() {
            FloodAlertEvent event = new FloodAlertEvent();
            event.setSensorId("SENSOR-FL-003");
            event.setSensorType("SOIL_MOISTURE");
            event.setSeverity("P2_ADVISORY");
            event.setConsecutiveCount(3);
            assertThat(event.getSensorId()).isEqualTo("SENSOR-FL-003");
            assertThat(event.getSensorType()).isEqualTo("SOIL_MOISTURE");
        }
    }

    // --- extractFloodReading ---

    @Nested
    @DisplayName("extractFloodReading — measurement extraction")
    class ExtractReadingTests {

        @Test
        @DisplayName("Extract rainfall from measurements map")
        void extractRainfall() {
            var reading = ThresholdCondition.extractFloodReading(
                    Map.of("rainfall", 75.0), "RAINFALL");
            assertThat(reading).isNotNull();
            assertThat(reading.sensorType).isEqualTo("RAINFALL");
            assertThat(reading.value).isEqualTo(75.0);
        }

        @Test
        @DisplayName("Extract water_level (case insensitive key)")
        void extractWaterLevel() {
            var reading = ThresholdCondition.extractFloodReading(
                    Map.of("Water_Level", 3.0), "WATER_LEVEL");
            assertThat(reading).isNotNull();
            assertThat(reading.value).isEqualTo(3.0);
        }

        @Test
        @DisplayName("Non-flood sensor type → null")
        void nonFloodSensor_null() {
            var reading = ThresholdCondition.extractFloodReading(
                    Map.of("aqi", 150.0), "AQI");
            assertThat(reading).isNull();
        }
    }
}
