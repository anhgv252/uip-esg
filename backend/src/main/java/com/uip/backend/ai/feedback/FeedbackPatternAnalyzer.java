package com.uip.backend.ai.feedback;

import com.uip.backend.alert.domain.AlertEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M4-COR-07: Detects patterns from 30-day alert feedback data.
 *
 * <p>Groups feedback records by trigger type (composed of
 * {@code module + ":" + measureType}) and computes:
 * <ul>
 *   <li>Total feedback count per trigger type</li>
 *   <li>Accuracy rate = correct / total</li>
 *   <li>High-false-positive trigger types where accuracy &lt; 70%</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class FeedbackPatternAnalyzer {

    /** Accuracy threshold below which a trigger type is considered high-false-positive. */
    static final double HIGH_FP_THRESHOLD = 0.70;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Calculates per-trigger-type accuracy rates from the given feedback records.
     *
     * @param feedbackRecords list of {@link AlertEvent} with non-null feedbackCorrect
     * @return map of triggerType → {@link TriggerAccuracy}
     */
    public Map<String, TriggerAccuracy> computeAccuracyByTrigger(List<AlertEvent> feedbackRecords) {
        Map<String, List<AlertEvent>> byTrigger = feedbackRecords.stream()
                .collect(Collectors.groupingBy(this::triggerKey));

        Map<String, TriggerAccuracy> result = byTrigger.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> computeAccuracy(e.getKey(), e.getValue())
                ));

        log.debug("[FeedbackPatternAnalyzer] Computed accuracy for {} trigger types", result.size());
        return result;
    }

    /**
     * Returns trigger types whose accuracy is below the high-FP threshold (70%).
     *
     * @param accuracyMap computed via {@link #computeAccuracyByTrigger}
     * @return list of trigger types needing suggestion improvements
     */
    public List<String> findHighFalsePositiveTriggers(Map<String, TriggerAccuracy> accuracyMap) {
        List<String> highFp = new ArrayList<>();
        for (Map.Entry<String, TriggerAccuracy> entry : accuracyMap.entrySet()) {
            if (entry.getValue().accuracyRate() < HIGH_FP_THRESHOLD) {
                highFp.add(entry.getKey());
                log.info("[FeedbackPatternAnalyzer] High-FP trigger: type={} accuracy={}",
                        entry.getKey(), entry.getValue().accuracyRate());
            }
        }
        return highFp;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Derives the trigger key: {@code MODULE:MEASURE_TYPE}. */
    public String triggerKey(AlertEvent event) {
        String module      = event.getModule() != null ? event.getModule() : "UNKNOWN";
        String measureType = event.getMeasureType() != null ? event.getMeasureType() : "UNKNOWN";
        return module + ":" + measureType;
    }

    private TriggerAccuracy computeAccuracy(String triggerType, List<AlertEvent> events) {
        long total   = events.size();
        long correct = events.stream()
                .filter(e -> Boolean.TRUE.equals(e.getFeedbackCorrect()))
                .count();
        double rate  = total > 0 ? (double) correct / total : 0.0;
        log.debug("[FeedbackPatternAnalyzer] triggerType={} total={} correct={} rate={}",
                triggerType, total, correct, rate);
        return new TriggerAccuracy(triggerType, total, correct, rate);
    }

    // ─── Value type ───────────────────────────────────────────────────────────

    /**
     * Immutable accuracy snapshot for one trigger type.
     *
     * @param triggerType   e.g. "AQI:TEMPERATURE"
     * @param totalFeedback total feedback records for this trigger
     * @param correctCount  operator-confirmed AI correct decisions
     * @param accuracyRate  correctCount / totalFeedback (0–1)
     */
    public record TriggerAccuracy(
            String triggerType,
            long   totalFeedback,
            long   correctCount,
            double accuracyRate
    ) {}
}
