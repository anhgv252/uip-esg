package com.uip.backend.tenant.api.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record TenantUsageDto(
        String tenantId,
        long readingCount,
        Instant from,
        Instant to
) {}
