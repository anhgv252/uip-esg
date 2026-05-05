package com.uip.backend.tenant.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record TenantUserDto(
        UUID userId,
        String username,
        String email,
        String role,
        boolean active,
        String tenantId
) {}
