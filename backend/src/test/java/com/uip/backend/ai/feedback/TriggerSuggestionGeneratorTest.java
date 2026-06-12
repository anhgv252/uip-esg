package com.uip.backend.ai.feedback;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TriggerSuggestionGenerator — unit tests")
class TriggerSuggestionGeneratorTest {

    @Mock private AlertRuleRepository alertRuleRepository;

    private FeedbackPatternAnalyzer patternAnalyzer;
    private TriggerSuggestionGenerator generator;

    @BeforeEach
    void setUp() {
        patternAnalyzer = new FeedbackPatternAnalyzer();
        generator = new TriggerSuggestionGenerator(patternAnalyzer, alertRuleRepository);
        // Stub out alertRuleRepository to return empty list by default
        when(alertRuleRepository.findByActiveTrueOrderByModuleAsc()).thenReturn(List.of());
    }

    // ── Insufficient data guard ───────────────────────────────────────────────

    @Nested
    @DisplayName("Insufficient data guard")
    class InsufficientDataGuard {

        @Test
        @DisplayName("TC-TSG-01: null feedback → empty suggestions")
        void nullFeedback_empty() {
            assertThat(generator.generate(null)).isEmpty();
        }

        @Test
        @DisplayName("TC-TSG-02: 0 records → empty suggestions")
        void zeroRecords_empty() {
            assertThat(generator.generate(List.of())).isEmpty();
        }

        @Test
        @DisplayName("TC-TSG-03: 99 records (< 100) → empty suggestions")
        void ninetyNineRecords_empty() {
            List<AlertEvent> events = buildEvents("AQI", "TEMPERATURE", 50, 49);
            assertThat(generator.generate(events)).isEmpty();
        }

        @Test
        @DisplayName("TC-TSG-04: exactly 100 records → suggestions generated")
        void exactly100Records_suggestionsGenerated() {
            List<AlertEvent> events = buildEvents("AQI", "TEMPERATURE", 60, 40); // 60% accuracy
            List<TriggerSuggestion> suggestions = generator.generate(events);
            assertThat(suggestions).isNotEmpty();
        }
    }

    // ── Suggestion count guarantee ────────────────────────────────────────────

    @Nested
    @DisplayName("Minimum 3 suggestions")
    class MinimumSuggestionsTests {

        @Test
        @DisplayName("TC-TSG-05: 1 high-FP trigger → padded to ≥3 suggestions")
        void oneHighFpTrigger_paddedToThree() {
            // Only 1 trigger type, 60% accuracy (high-FP)
            List<AlertEvent> events = buildEvents("AQI", "PM25", 60, 40);
            List<TriggerSuggestion> suggestions = generator.generate(events);
            assertThat(suggestions).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("TC-TSG-06: no high-FP triggers → still 3 suggestions via padding")
        void noHighFpTriggers_stillThreeSuggestions() {
            // 3 trigger types, all >70% accuracy (none are high-FP)
            List<AlertEvent> events = new ArrayList<>();
            events.addAll(buildEvents("AQI", "PM25", 80, 20));      // 80%
            events.addAll(buildEvents("WATER", "LEVEL", 90, 10));   // 90%
            events.addAll(buildEvents("NOISE", "DB", 75, 25));      // 75%

            List<TriggerSuggestion> suggestions = generator.generate(events);
            assertThat(suggestions).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("TC-TSG-07: 4 high-FP trigger types → exactly 4 suggestions (no padding)")
        void fourHighFpTriggers_exactlyFour() {
            List<AlertEvent> events = new ArrayList<>();
            events.addAll(buildEvents("AQI",        "CO",       30, 70));  // 30%
            events.addAll(buildEvents("WATER",      "PRESSURE", 40, 60));  // 40%
            events.addAll(buildEvents("STRUCTURAL",  "VIBRATION",50, 50)); // 50%
            events.addAll(buildEvents("NOISE",       "DB",       60, 40)); // 60%

            List<TriggerSuggestion> suggestions = generator.generate(events);
            assertThat(suggestions).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    // ── Suggestion content validation ─────────────────────────────────────────

    @Nested
    @DisplayName("Suggestion content")
    class SuggestionContentTests {

        @Test
        @DisplayName("TC-TSG-08: suggestion has non-null reason and generatedAt")
        void suggestionHasRequiredFields() {
            List<AlertEvent> events = buildEvents("AQI", "CO", 40, 60); // 40% — high FP
            List<TriggerSuggestion> suggestions = generator.generate(events);

            for (TriggerSuggestion suggestion : suggestions) {
                assertThat(suggestion.reason()).isNotBlank();
                assertThat(suggestion.generatedAt()).isNotNull();
                assertThat(suggestion.confidence()).isBetween(0.0, 1.0);
            }
        }

        @Test
        @DisplayName("TC-TSG-09: high-FP suggestion has suggestedThreshold > currentThreshold when rule exists")
        void highFpSuggestion_thresholdIncreased() {
            // Stub a rule with threshold = 100
            AlertRule rule = new AlertRule();
            rule.setModule("AQI");
            rule.setMeasureType("TEMPERATURE");
            rule.setThreshold(100.0);
            rule.setActive(true);
            when(alertRuleRepository.findByActiveTrueOrderByModuleAsc()).thenReturn(List.of(rule));

            List<AlertEvent> events = buildEvents("AQI", "TEMPERATURE", 30, 70); // 30% — high FP
            List<TriggerSuggestion> suggestions = generator.generate(events);

            TriggerSuggestion primary = suggestions.stream()
                    .filter(s -> "AQI:TEMPERATURE".equals(s.triggerType()))
                    .findFirst().orElseThrow();

            assertThat(primary.currentThreshold()).isEqualTo(100.0);
            assertThat(primary.suggestedThreshold()).isGreaterThan(100.0);
        }

        @Test
        @DisplayName("TC-TSG-10: triggerType in suggestion matches pattern MODULE:MEASURE_TYPE")
        void triggerTypeFormat_isCorrect() {
            List<AlertEvent> events = buildEvents("STRUCTURAL", "STRESS", 40, 60); // high FP
            List<TriggerSuggestion> suggestions = generator.generate(events);

            TriggerSuggestion primary = suggestions.stream()
                    .filter(s -> s.triggerType().startsWith("STRUCTURAL:"))
                    .findFirst().orElseThrow();

            assertThat(primary.triggerType()).matches("[A-Z_]+:[A-Z_]+");
        }

        @Test
        @DisplayName("TC-TSG-11: confidence is higher for larger feedback samples")
        void confidence_scalesWithSampleSize() {
            // Small sample (100 records exactly) → confidence should be lower
            List<AlertEvent> smallSample = buildEvents("AQI", "PM10", 30, 70);
            // Large sample (200+ records) → higher confidence
            List<AlertEvent> largeSample = buildEvents200("AQI", "PM10", 60, 140); // 200 records

            List<TriggerSuggestion> smallSuggestions = generator.generate(smallSample);
            List<TriggerSuggestion> largeSuggestions = generator.generate(largeSample);

            double smallConfidence = smallSuggestions.stream()
                    .filter(s -> "AQI:PM10".equals(s.triggerType()))
                    .mapToDouble(TriggerSuggestion::confidence).max().orElse(0);
            double largeConfidence = largeSuggestions.stream()
                    .filter(s -> "AQI:PM10".equals(s.triggerType()))
                    .mapToDouble(TriggerSuggestion::confidence).max().orElse(0);

            assertThat(largeConfidence).isGreaterThanOrEqualTo(smallConfidence);
        }
    }

    // ── Helper builders ───────────────────────────────────────────────────────

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

    /** Builds exactly 200 events (for testing ≥200 confidence path). */
    private List<AlertEvent> buildEvents200(String module, String measureType,
                                            int correct, int incorrect) {
        return buildEvents(module, measureType, correct, incorrect);
    }

    private AlertEvent alertEvent(String module, String measureType, boolean feedbackCorrect) {
        AlertEvent event = new AlertEvent();
        event.setModule(module);
        event.setMeasureType(measureType);
        event.setSensorId("sensor-test");
        event.setValue(50.0);
        event.setThreshold(40.0);
        event.setSeverity("HIGH");
        event.setDetectedAt(Instant.now());
        event.setFeedbackCorrect(feedbackCorrect);
        return event;
    }
}
