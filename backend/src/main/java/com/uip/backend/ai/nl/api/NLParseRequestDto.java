package com.uip.backend.ai.nl.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for NL→BPMN parse endpoint.
 */
public record NLParseRequestDto(
    @NotBlank(message = "text must not be blank")
    @Size(max = 2000, message = "text must not exceed 2000 characters")
    String text,
    
    String workflowContext  // Optional: workflow type hint
) {}
