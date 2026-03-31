package com.uip.backend.alert.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AlertRuleRequest {

    @NotBlank
    private String ruleName;

    @NotBlank
    private String module;

    @NotBlank
    private String measureType;

    @NotBlank
    private String operator;

    @NotNull
    private Double threshold;

    @NotBlank
    private String severity;

    @Positive
    private int cooldownMinutes = 10;
}
