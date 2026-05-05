package com.uip.backend.tenant.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
public record InviteUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "ROLE_OPERATOR|ROLE_TENANT_ADMIN") String role
) {}
