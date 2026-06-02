package com.uip.backend.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateFeatureRequest(
        @NotBlank String featureKey,
        @NotNull Boolean enabled
) {}
