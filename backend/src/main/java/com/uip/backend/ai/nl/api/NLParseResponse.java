package com.uip.backend.ai.nl.api;

import java.util.Map;

/**
 * Response DTO for NL→BPMN parse endpoint.
 */
public record NLParseResponse(
    String intent,               // One of 10 MVP4 workflow types or "UNKNOWN"
    double confidence,           // 0.0–1.0
    Map<String, String> entities, // Extracted entities (may be null)
    String model,                // "cloud" or "local"
    long latencyMs,              // Total inference latency
    String bpmnTemplate          // Optional: instantiated BPMN XML (T03)
) {}
