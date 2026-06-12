package com.uip.backend.ai.feedback;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * M4-COR-07: Generates improved trigger suggestions from 30-day feedback patterns.
 *
 * <p>Conditions for suggestion generation:
 * <ol>
 *   <li>At least {@value MIN_FEEDBACK_RECORDS} total feedback records collected.</li>
 *   <li>One or more trigger types identified as high-false-positive (accuracy &lt; 70%).</li>
 * </ol>
 * </p>
 *
 * <p>Suggestion algorithm: for each high-FP trigger type, look up the existing
 * {@link com.uip.backend.alert.domain.AlertRule} threshold. Suggest adjusting it
 * upward by a confidence-weighted factor derived from the false-positive rate.
 * Always generates at least 3 suggestions (pads with general hygiene suggestions
 * when fewer high-FP triggers exist).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerSuggestionGenerator {

    /** Minimum feedback records required before generating suggestions. */
    static final int MIN_FEEDBACK_RECORDS = 100;

    private final FeedbackPatternAnalyzer    patternAnalyzer;
    private final AlertRuleRepository        alertRuleRepository;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Generates improved trigger suggestions from the given feedback records.
     *
     * <p>Returns an empty list if fewer than {@value MIN_FEEDBACK_RECORDS} records
     * are provided.</p>
     *
     * @param feedbackRecords 30-day alert feedback — may be empty
     * @return list of suggestions; at least 3 when data is sufficient
     */
    public List<TriggerSuggestion> generate(List<AlertEvent> feedbackRecords) {
        if (feedbackRecords == null || feedbackRecords.size() < MIN_FEEDBACK_RECORDS) {
            log.info("[TriggerSuggestionGenerator] Insufficient feedback ({}) — min required {}",
                    feedbackRecords == null ? 0 : feedbackRecords.size(), MIN_FEEDBACK_RECORDS);
            return List.of();
        }

        Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> accuracyMap =
                patternAnalyzer.computeAccuracyByTrigger(feedbackRecords);

        List<String> highFpTriggers = patternAnalyzer.findHighFalsePositiveTriggers(accuracyMap);

        List<TriggerSuggestion> suggestions = new ArrayList<>();

        // Generate one suggestion per high-FP trigger type
        for (String triggerType : highFpTriggers) {
            FeedbackPatternAnalyzer.TriggerAccuracy accuracy = accuracyMap.get(triggerType);
            buildSuggestionForTrigger(triggerType, accuracy).ifPresent(suggestions::add);
        }

        // Pad to ≥3 with general hygiene suggestions
        padWithGeneralSuggestions(suggestions, accuracyMap);

        log.info("[TriggerSuggestionGenerator] Generated {} suggestions from {} feedback records",
                suggestions.size(), feedbackRecords.size());
        return List.copyOf(suggestions);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Optional<TriggerSuggestion> buildSuggestionForTrigger(
            String triggerType, FeedbackPatternAnalyzer.TriggerAccuracy accuracy) {

        String[] parts      = triggerType.split(":", 2);
        String module       = parts.length > 0 ? parts[0] : "";
        String measureType  = parts.length > 1 ? parts[1] : "";

        // Lookup the active rule for this trigger type from alert_rules
        double currentThreshold = alertRuleRepository
                .findByActiveTrueOrderByModuleAsc()
                .stream()
                .filter(r -> module.equals(r.getModule()) && measureType.equals(r.getMeasureType()))
                .mapToDouble(r -> r.getThreshold() != null ? r.getThreshold() : 0.0)
                .findFirst()
                .orElse(0.0);

        // False-positive rate drives the adjustment factor (more FP → larger adjustment)
        double fpRate             = 1.0 - accuracy.accuracyRate();
        double adjustmentFactor   = 1.0 + (fpRate * 0.20); // up to +20% threshold increase
        double suggestedThreshold = currentThreshold > 0
                ? currentThreshold * adjustmentFactor
                : 10.0; // sensible default when no rule found

        double confidence = accuracy.totalFeedback() >= 200 ? 0.85
                : accuracy.totalFeedback() >= 100 ? 0.70
                : 0.55;

        String reason = String.format(
                "Trigger type '%s' has %.0f%% false-positive rate over %d feedback records. "
                + "Increasing threshold by %.0f%% reduces alert noise while preserving true positives.",
                triggerType,
                fpRate * 100,
                accuracy.totalFeedback(),
                (adjustmentFactor - 1.0) * 100
        );

        return Optional.of(new TriggerSuggestion(
                triggerType,
                currentThreshold,
                suggestedThreshold,
                confidence,
                reason,
                Instant.now()
        ));
    }

    /**
     * Pads the suggestion list with general hygiene suggestions to ensure ≥3 entries.
     * Each padding suggestion targets one of the trigger types with the most feedback.
     */
    private void padWithGeneralSuggestions(
            List<TriggerSuggestion> suggestions,
            Map<String, FeedbackPatternAnalyzer.TriggerAccuracy> accuracyMap) {

        if (suggestions.size() >= 3) return;

        // Sort all trigger types by total feedback descending and pick the top ones
        List<String> sortedByFeedback = accuracyMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(
                        b.getValue().totalFeedback(), a.getValue().totalFeedback()))
                .map(Map.Entry::getKey)
                .toList();

        // Already-added trigger types
        java.util.Set<String> alreadyAdded = new java.util.HashSet<>();
        suggestions.forEach(s -> alreadyAdded.add(s.triggerType()));

        for (String triggerType : sortedByFeedback) {
            if (suggestions.size() >= 3) break;
            if (alreadyAdded.contains(triggerType)) continue;

            FeedbackPatternAnalyzer.TriggerAccuracy acc = accuracyMap.get(triggerType);

            // Generate a cooldown-reduction suggestion for well-performing triggers
            String reason = String.format(
                    "Trigger type '%s' shows %.0f%% accuracy over %d records. "
                    + "Consider reducing cooldown period to improve responsiveness.",
                    triggerType,
                    acc.accuracyRate() * 100,
                    acc.totalFeedback()
            );

            suggestions.add(new TriggerSuggestion(
                    triggerType,
                    0.0,  // threshold not changed — cooldown suggestion
                    0.0,
                    0.60,
                    reason,
                    Instant.now()
            ));
            alreadyAdded.add(triggerType);
        }

        // Last resort: add generic review suggestion if still below 3
        while (suggestions.size() < 3) {
            suggestions.add(new TriggerSuggestion(
                    "GENERAL:REVIEW",
                    0.0,
                    0.0,
                    0.50,
                    "Review all active alert rules for threshold calibration based on recent feedback data.",
                    Instant.now()
            ));
        }
    }
}
