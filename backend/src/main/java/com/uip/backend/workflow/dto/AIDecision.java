package com.uip.backend.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AIDecision {
    private String decision;
    private String reasoning;
    private double confidence;
    @JsonProperty("recommended_actions")
    private List<String> recommendedActions;
    private String severity;
}
