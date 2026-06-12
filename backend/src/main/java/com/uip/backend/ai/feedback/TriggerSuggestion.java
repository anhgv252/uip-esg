package com.uip.backend.ai.feedback;

import java.time.Instant;

/**
 * M4-COR-07: Immutable trigger suggestion produced by {@link TriggerSuggestionGenerator}.
 *
 * @param triggerType         e.g. "AQI:TEMPERATURE" (module:measureType)
 * @param currentThreshold    existing alert threshold value from AlertRule
 * @param suggestedThreshold  AI-recommended adjusted threshold
 * @param confidence          confidence score 0.0–1.0 for this suggestion
 * @param reason              human-readable explanation of the suggestion
 * @param generatedAt         when this suggestion was generated
 */
public record TriggerSuggestion(
        String  triggerType,
        double  currentThreshold,
        double  suggestedThreshold,
        double  confidence,
        String  reason,
        Instant generatedAt
) {}
