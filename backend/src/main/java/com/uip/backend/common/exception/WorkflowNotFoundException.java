package com.uip.backend.common.exception;

/**
 * Thrown when a Camunda process definition or instance cannot be found.
 */
public class WorkflowNotFoundException extends RuntimeException {
    public WorkflowNotFoundException(String message) {
        super(message);
    }
}
