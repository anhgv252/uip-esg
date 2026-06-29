package com.uip.backend.ai.nl.domain;

/**
 * Model inference routing target for NL→BPMN requests.
 * 
 * <p>ADR-049 §4.2: Hybrid routing — LOCAL for PII/gdpr_mode, CLOUD for non-PII.
 */
public enum Route {
    /**
     * Route to on-prem ViT5 model (Vietnam infra, Decree 13/2023 compliant).
     * SLA: p95 ≤ 8s, intent hit rate ≥ 80%, timeout 10s.
     */
    LOCAL,
    
    /**
     * Route to Claude API (Anthropic US infrastructure, non-PII only).
     * SLA: p95 ≤ 4s, intent hit rate ~92%, timeout 6s.
     */
    CLOUD
}
