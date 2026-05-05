package com.uip.backend.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record UpdateSettingsRequest(
        @NotBlank String configKey,
        @NotBlank String configValue
) {}
