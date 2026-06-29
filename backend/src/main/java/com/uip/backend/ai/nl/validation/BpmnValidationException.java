package com.uip.backend.ai.nl.validation;

/**
 * Thrown when AI-generated BPMN fails validation (XSD or custom rules).
 *
 * <p>Maps to HTTP 422 Unprocessable Entity — invalid BPMN never reaches the operator
 * review screen (fail-before-review-UI pattern, ADR-049 §4).
 *
 * <p>M5-2 T10
 */
public class BpmnValidationException extends RuntimeException {

    private final ValidationResult result;

    public BpmnValidationException(ValidationResult result) {
        super("BPMN validation failed: " + result.errors());
        this.result = result;
    }

    public ValidationResult getResult() {
        return result;
    }
}
