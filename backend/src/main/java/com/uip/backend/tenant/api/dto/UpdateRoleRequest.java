package com.uip.backend.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
public record UpdateRoleRequest(
        @NotBlank @Pattern(regexp = "ROLE_OPERATOR|ROLE_TENANT_ADMIN|ROLE_CITIZEN") String role
) {}
