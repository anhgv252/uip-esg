package com.uip.backend.tenant.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class TenantConfigResponse {
    private String tenantId;
    private Map<String, FeatureFlag> features;
    private Branding branding;

    @Getter
    @Builder
    public static class FeatureFlag {
        private boolean enabled;
    }

    @Getter
    @Builder
    public static class Branding {
        private String partnerName;
        private String primaryColor;
        private String logoUrl;
    }
}
