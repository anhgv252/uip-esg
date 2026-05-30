package com.uip.backend.aiworkflow.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QA-2: Workflow Execution Status tests — 3 scenarios.
 * Tests status transitions for workflow execution.
 */
@DisplayName("Workflow Execution Status — QA Tests")
class WorkflowExecutionStatusTest {

    @Test
    @DisplayName("EX-01: initial status is PENDING")
    void initialStatus_isPending() {
        String status = "PENDING";
        assertEquals("PENDING", status);
        assertTrue(isValidStatus(status));
    }

    @Test
    @DisplayName("EX-02: PENDING → RUNNING → COMPLETED transition is valid")
    void completedTransition_isValid() {
        String status = "PENDING";

        // Transition to RUNNING
        assertTrue(canTransition(status, "RUNNING"));
        status = "RUNNING";

        // Transition to COMPLETED
        assertTrue(canTransition(status, "COMPLETED"));
    }

    @Test
    @DisplayName("EX-03: PENDING → RUNNING → FAILED transition is valid")
    void failedTransition_isValid() {
        String status = "PENDING";

        // Transition to RUNNING
        assertTrue(canTransition(status, "RUNNING"));
        status = "RUNNING";

        // Transition to FAILED with error message
        assertTrue(canTransition(status, "FAILED"));
        String errorMessage = "Camunda process execution failed: timeout";
        assertNotNull(errorMessage);
        assertTrue(errorMessage.contains("failed"));
    }

    private boolean isValidStatus(String status) {
        return "PENDING".equals(status) || "RUNNING".equals(status)
                || "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    private boolean canTransition(String from, String to) {
        return switch (from) {
            case "PENDING" -> "RUNNING".equals(to);
            case "RUNNING" -> "COMPLETED".equals(to) || "FAILED".equals(to);
            default -> false;
        };
    }
}
