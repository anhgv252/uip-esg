package com.uip.backend.aiworkflow.gateway;

import lombok.Data;

/**
 * Local DTO for AI decisions within the aiworkflow module.
 * Avoids cross-module dependency on workflow.dto.AIDecision.
 */
@Data
public class AiDecisionInput {
    private String decision;
    private String reasoning;
    private double confidence;
    private String severity;
}
