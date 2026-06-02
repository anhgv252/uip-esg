package com.uip.backend.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[a-z0-9_-]+$", message = "tenantId must be lowercase alphanumeric, hyphen, or underscore")
        String tenantId,

        @NotBlank @Size(max = 100)
        String tenantName,

        @Pattern(regexp = "^T[123]$", message = "tier must be T1, T2, or T3")
        String tier,

        String locationPath
) {}
