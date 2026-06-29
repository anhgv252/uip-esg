package com.uip.backend.ai.nl.domain;

/**
 * Single step in BPMN workflow simulation.
 *
 * @param elementId   BPMN element ID (e.g., "notifyResidents", "br010OperatorReview")
 * @param elementType BPMN element type (userTask, serviceTask, startEvent, endEvent, etc.)
 * @param elementName Human-readable element name from BPMN
 * @param status      Step execution status (OK, SKIPPED, FAILED)
 * @param message     Optional detail message (for warnings or errors)
 */
public record SimulationStep(
    String elementId,
    String elementType,
    String elementName,
    StepStatus status,
    String message
) {
    public enum StepStatus {
        OK,
        SKIPPED,
        FAILED
    }
}
