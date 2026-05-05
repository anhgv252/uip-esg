package com.uip.backend.tenant.api.dto;

import lombok.Builder;

import java.util.Map;

@Builder
public record TenantSettingsDto(
        String tenantId,
        Map<String, String> configEntries,
        Branding branding
) {
    @Builder
    public record Branding(
            String primaryColor,
            String partnerName,
            String logoUrl
    ) {}
}
