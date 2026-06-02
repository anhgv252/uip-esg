package com.uip.backend.tenant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.tenant.api.dto.TenantConfigResponse;
import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.domain.Tenant;
import com.uip.backend.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantConfigService {

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TenantConfigResponse getCurrentTenantConfig() {
        String tenantId = TenantContext.getCurrentTenant();

        return tenantRepository.findByTenantId(tenantId)
                .map(this::buildConfig)
                .orElseGet(this::buildDefaultConfig);
    }

    private TenantConfigResponse buildConfig(Tenant tenant) {
        Map<String, TenantConfigResponse.FeatureFlag> features = parseFeatures(tenant.getConfigJson());

        return TenantConfigResponse.builder()
                .tenantId(tenant.getTenantId())
                .features(features)
                .branding(TenantConfigResponse.Branding.builder()
                        .partnerName("UIP Smart City")
                        .primaryColor("#1976D2")
                        .logoUrl(null)
                        .build())
                .build();
    }

    private TenantConfigResponse buildDefaultConfig() {
        Map<String, TenantConfigResponse.FeatureFlag> defaults = new LinkedHashMap<>();
        defaults.put("environment-module", TenantConfigResponse.FeatureFlag.builder().enabled(true).build());
        defaults.put("esg-module", TenantConfigResponse.FeatureFlag.builder().enabled(true).build());
        defaults.put("traffic-module", TenantConfigResponse.FeatureFlag.builder().enabled(true).build());
        defaults.put("citizen-portal", TenantConfigResponse.FeatureFlag.builder().enabled(true).build());
        defaults.put("ai-workflow", TenantConfigResponse.FeatureFlag.builder().enabled(true).build());
        defaults.put("city-ops", TenantConfigResponse.FeatureFlag.builder().enabled(true).build());

        return TenantConfigResponse.builder()
                .tenantId("default")
                .features(defaults)
                .branding(TenantConfigResponse.Branding.builder()
                        .partnerName("UIP Smart City")
                        .primaryColor("#1976D2")
                        .logoUrl(null)
                        .build())
                .build();
    }

    private Map<String, TenantConfigResponse.FeatureFlag> parseFeatures(String configJson) {
        Map<String, TenantConfigResponse.FeatureFlag> defaults = buildDefaultConfig().getFeatures();
        if (configJson == null || configJson.isBlank()) {
            return defaults;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(configJson, new TypeReference<>() {});
            Object featuresObj = root.get("features");
            if (!(featuresObj instanceof Map<?, ?> rawFeatures)) {
                return defaults;
            }
            Map<String, TenantConfigResponse.FeatureFlag> result = new LinkedHashMap<>(defaults);
            rawFeatures.forEach((k, v) -> {
                if (k instanceof String key && v instanceof Map<?, ?> flagMap) {
                    Object enabledVal = flagMap.get("enabled");
                    boolean enabled = enabledVal instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(enabledVal));
                    result.put(key, TenantConfigResponse.FeatureFlag.builder().enabled(enabled).build());
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse tenant config JSON, using defaults: {}", e.getMessage());
            return defaults;
        }
    }
}
