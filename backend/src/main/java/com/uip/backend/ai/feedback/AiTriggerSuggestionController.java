package com.uip.backend.ai.feedback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M4-COR-07: REST endpoint exposing AI-generated trigger suggestions.
 *
 * <p>Access restricted to {@code ADMIN} role. Collects 30-day feedback,
 * runs pattern analysis, and returns ≥3 suggestions when sufficient data exists.</p>
 *
 * <pre>
 * GET /api/v1/ai/trigger-suggestions → 200 OK  List&lt;TriggerSuggestion&gt;
 *                                     200 OK  [] (insufficient data)
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/trigger-suggestions")
@RequiredArgsConstructor
public class AiTriggerSuggestionController {

    private final IncidentFeedbackAggregator   feedbackAggregator;
    private final TriggerSuggestionGenerator   suggestionGenerator;

    /**
     * Returns AI-generated trigger suggestions based on 30-day operator feedback.
     *
     * <p>Returns an empty list if fewer than 100 feedback records exist.
     * Returns ≥3 suggestions when data is sufficient.</p>
     *
     * @return 200 with list of {@link TriggerSuggestion} (may be empty)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TriggerSuggestion>> getTriggerSuggestions() {
        log.info("[AiTriggerSuggestions] Request received for trigger suggestions");

        var feedbackRecords = feedbackAggregator.collectRecentFeedback();
        var suggestions     = suggestionGenerator.generate(feedbackRecords);

        log.info("[AiTriggerSuggestions] Returning {} suggestions (feedback_records={})",
                suggestions.size(), feedbackRecords.size());

        return ResponseEntity.ok(suggestions);
    }
}
