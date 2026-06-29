package com.uip.backend.ai.nl.domain;

/**
 * Lifecycle states for AI-generated workflow drafts.
 *
 * <p>M5-3 T01: Operator review workflow
 * <ul>
 *   <li>PENDING_REVIEW: Draft created, awaiting operator decision</li>
 *   <li>APPROVED: Operator approved, ready for simulation or execution</li>
 *   <li>REJECTED: Operator rejected, workflow will not be deployed</li>
 *   <li>SIMULATED: Dry-run test passed (T04)</li>
 *   <li>EXECUTED: Workflow successfully deployed to BPMN engine</li>
 * </ul>
 */
public enum DraftStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    SIMULATED,
    EXECUTED
}
