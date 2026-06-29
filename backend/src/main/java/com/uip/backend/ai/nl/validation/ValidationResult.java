package com.uip.backend.ai.nl.validation;

import java.util.List;

/**
 * Result of BPMN validation (XSD + custom rules).
 *
 * <p>M5-2 T10: Returned by {@link BpmnValidationService} and {@link BpmnCustomValidator}.
 *
 * @param valid  true when all rules pass
 * @param errors ordered list of human-readable error codes (empty when valid)
 */
public record ValidationResult(boolean valid, List<String> errors) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** Convenience factory for the happy path. */
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    /** Alias for {@link #valid()} — matches conventional boolean getter naming. */
    public boolean isValid() {
        return valid;
    }
}
