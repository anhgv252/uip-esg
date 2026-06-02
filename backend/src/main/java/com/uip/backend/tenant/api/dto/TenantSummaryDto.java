package com.uip.backend.tenant.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TenantSummaryDto(
        UUID id,
        String tenantId,
        String tenantName,
        String tier,
        boolean active,
        String locationPath,
        Instant createdAt
) {}
