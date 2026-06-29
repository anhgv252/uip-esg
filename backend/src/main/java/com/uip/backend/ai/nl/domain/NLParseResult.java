package com.uip.backend.ai.nl.domain;

import java.util.Map;

/**
 * Result of NL→BPMN intent parsing from Claude or local model.
 */
public record NLParseResult(
    String intent,                     // One of 10 MVP4 workflow types or "UNKNOWN"
    double confidence,                 // 0.0–1.0
    Map<String, String> entities,      // Extracted entities (zone, threshold, etc.)
    Route modelUsed,                   // Which route served this request
    long latencyMs,                    // Total inference latency
    String bpmnTemplate                // Optional: instantiated BPMN XML (T03)
) {
    public NLParseResult {
        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException("intent must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]");
        }
        if (modelUsed == null) {
            throw new IllegalArgumentException("modelUsed must not be null");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be >= 0");
        }
    }
}
