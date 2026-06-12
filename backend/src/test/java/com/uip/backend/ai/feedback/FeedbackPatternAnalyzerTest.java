package com.uip.backend.ai.feedback;

import com.uip.backend.alert.domain.AlertEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeedbackPatternAnalyzer — unit tests")
class FeedbackPatternAnalyzerTest {

    private FeedbackPatternAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new FeedbackPatternAnalyzer();
    }

    // ── triggerKey ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("triggerKey")
    class TriggerKeyTests {

        @Test
        @DisplayName("TC-FPA-01: builds MODULE:MEASURE_TYPE key from alert event")
        void buildsCorrectTriggerKey() {
            AlertEvent event = alertEvent("AQI", "TEMPERATURE", true);
            assertThat(analyzer.triggerKey(event)).isEqualTo("AQI:TEMPERATURE");
        }

        @Test
        @DisplayName("TC-FPA-02: null module defaults to UNKNOWN")
        void nullModule_defaultsToUnknown() {
            AlertEvent event = alertEvent(null, "AQI", true);
            assertThat(analyzer.triggerKey(event)).isEqualTo("UNKNOWN:AQI");
        }

        @Test
        @DisplayName("TC-FPA-03: null measureType defaults to UNKNOWN")
        void nullMeasureType_defaultsToUnknown() {
            AlertEvent event = alertEvent("WATER", null, true);
            assertThat(analyzer.triggerKey(event)).isEqualTo("WATER:UNKNOWN");
        }
    }

    // ── computeAccuracyByTrigger ──────────────────────────────────────────────

    @Nested
    @DisplayName("computeAccuracyByTrigger")
    class ComputeAccuracyTests {

        @Test
        @DisplayName("TC-FPA-04: 10 correct + 0 incorrect → 100% accuracy")
        void allCorrect_hundredPercent() {
            List<AlertEvent> events = buildEvents("AQI", "TEMPERATURE", 10, 0);
            Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> result =
                    analyzer.computeAccuracyByTrigger(events);

            assertThat(result).containsKey("AQI:TEMPERATURE");
            FeedbackPatternAnalyzer.TriggerAccuracy acc = result.get("AQI:TEMPERATURE");
            assertThat(acc.accuracyRate()).isEqualTo(1.0);
            assertThat(acc.totalFeedback()).isEqualTo(10);
            assertThat(acc.correctCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("TC-FPA-05: 0 correct + 10 incorrect → 0% accuracy")
        void allIncorrect_zeroPercent() {
            List<AlertEvent> events = buildEvents("WATER", "LEVEL", 0, 10);
            Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> result =
                    analyzer.computeAccuracyByTrigger(events);

            FeedbackPatternAnalyzer.TriggerAccuracy acc = result.get("WATER:LEVEL");
            assertThat(acc.accuracyRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("TC-FPA-06: 6 correct + 4 incorrect → 60% accuracy")
        void mixed_sixtyPercent() {
            List<AlertEvent> events = buildEvents("STRUCTURAL", "VIBRATION", 6, 4);
            Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> result =
                    analyzer.computeAccuracyByTrigger(events);

            FeedbackPatternAnalyzer.TriggerAccuracy acc = result.get("STRUCTURAL:VIBRATION");
            assertThat(acc.accuracyRate()).isEqualTo(0.6);
        }

        @Test
        @DisplayName("TC-FPA-07: multiple trigger types grouped independently")
        void multipleTypes_groupedIndependently() {
            List<AlertEvent> events = new ArrayList<>();
            events.addAll(buildEvents("AQI", "TEMPERATURE", 8, 2));
            events.addAll(buildEvents("NOISE", "DECIBEL", 3, 7));

            Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> result =
                    analyzer.computeAccuracyByTrigger(events);

            assertThat(result).hasSize(2);
            assertThat(result.get("AQI:TEMPERATURE").accuracyRate()).isEqualTo(0.8);
            assertThat(result.get("NOISE:DECIBEL").accuracyRate()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("TC-FPA-08: empty list → empty map")
        void emptyList_emptyMap() {
            Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> result =
                    analyzer.computeAccuracyByTrigger(List.of());
            assertThat(result).isEmpty();
        }
    }

    // ── findHighFalsePositiveTriggers ─────────────────────────────────────────

    @Nested
    @DisplayName("findHighFalsePositiveTriggers")
    class FindHighFalsePositiveTests {

        @Test
        @DisplayName("TC-FPA-09: accuracy < 70% → classified as high-FP")
        void belowThreshold_classifiedAsHighFp() {
            List<AlertEvent> events = buildEvents("AQI", "CO", 6, 4); // 60% < 70%
            Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> accuracy =
                    analyzer.computeAccuracyByTrigger(events);

            List<String> highFp = analyzer.findHighFalsePositiveTriggers(accuracy);
            assertThat(highFp).contains("AQI:CO");
        }

        @Test
        @DisplayName("TC-FPA-10: accuracy = 70% exactly → NOT classified as high-FP (boundary)")
        void exactlyAtThreshold_notHighFp() {
            List<AlertEvent> events = buildEvents("STRUCTURAL", "STRESS", 7, 3); // 70%
            Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> accuracy =
                    analyzer.computeAccuracyByTrigger(events);

            List<String> highFp = analyzer.findHighFalsePositiveTriggers(accuracy);
            assertThat(highFp).doesNotContain("STRUCTURAL:STRESS");
        }

        @Test
        @DisplayName("TC-FPA-11: accuracy > 70% → not in high-FP list")
        void aboveThreshold_notHighFp() {
            List<AlertEvent> events = buildEvents("WATER", "FLOW", 8, 2); // 80%
            Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> accuracy =
                    analyzer.computeAccuracyByTrigger(events);

            List<String> highFp = analyzer.findHighFalsePositiveTriggers(accuracy);
            assertThat(highFp).doesNotContain("WATER:FLOW");
        }
    }

    // ── Helper builders ───────────────────────────────────────────────────────

    /**
     * Creates {@code correct + incorrect} AlertEvent instances for the given module/measureType.
     */
    private List<AlertEvent> buildEvents(String module, String measureType,
                                         int correct, int incorrect) {
        List<AlertEvent> events = new ArrayList<>();
        for (int i = 0; i < correct; i++) {
            events.add(alertEvent(module, measureType, true));
        }
        for (int i = 0; i < incorrect; i++) {
            events.add(alertEvent(module, measureType, false));
        }
        return events;
    }

    private AlertEvent alertEvent(String module, String measureType, boolean feedbackCorrect) {
        AlertEvent event = new AlertEvent();
        event.setModule(module);
        event.setMeasureType(measureType);
        event.setSensorId("sensor-1");
        event.setValue(50.0);
        event.setThreshold(40.0);
        event.setSeverity("HIGH");
        event.setDetectedAt(java.time.Instant.now());
        event.setFeedbackCorrect(feedbackCorrect);
        return event;
    }
}
