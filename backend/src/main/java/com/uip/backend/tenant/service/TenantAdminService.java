package com.uip.backend.tenant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.tenant.api.dto.*;
import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.domain.Tenant;
import com.uip.backend.tenant.domain.TenantConfigEntry;
import com.uip.backend.tenant.domain.TenantConfigEntryId;
import com.uip.backend.tenant.repository.TenantConfigRepository;
import com.uip.backend.tenant.repository.TenantRepository;
import com.uip.backend.tenant.service.InviteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAdminService {

    private final TenantRepository tenantRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final AppUserRepository appUserRepository;
    private final InviteService inviteService;
    private final ObjectMapper objectMapper;

    public String resolveEffectiveTenantId(String pathTenantId, Authentication auth) {
        if (hasRole(auth, "ROLE_ADMIN")) {
            return pathTenantId;
        }
        if (hasRole(auth, "ROLE_TENANT_ADMIN")) {
            String jwtTenantId = TenantContext.getCurrentTenant();
            if (!jwtTenantId.equals(pathTenantId)) {
                log.warn("TENANT_ADMIN {} attempted path={}, serving own tenant",
                        sanitizeLog(jwtTenantId), sanitizeLog(pathTenantId));
            }
            return jwtTenantId;
        }
        throw new AccessDeniedException("Access denied for role");
    }

    public void requireTenantAdminScope(Authentication auth) {
        boolean hasScope = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("tenant:admin"));
        if (!hasScope && !hasRole(auth, "ROLE_ADMIN")) {
            throw new AccessDeniedException("Requires tenant:admin scope");
        }
    }

    @Transactional
    public void inviteUser(String tenantId, InviteUserRequest request, String invitedBy) {
        inviteService.createInvite(tenantId, request, invitedBy);
    }

    @Transactional(readOnly = true)
    public List<TenantUserDto> listUsers(String tenantId) {
        return appUserRepository.findByTenantId(tenantId).stream()
                .map(u -> TenantUserDto.builder()
                        .userId(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .role(u.getRole().name())
                        .active(u.isActive())
                        .tenantId(u.getTenantId())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateUserRole(String tenantId, UUID userId, String newRole) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!user.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("User does not belong to tenant: " + tenantId);
        }
        user.setRole(UserRole.valueOf(newRole));
        log.info("User role updated: userId={} tenant={} newRole={}",
                sanitizeLog(String.valueOf(userId)), sanitizeLog(tenantId), sanitizeLog(newRole));
    }

    @Transactional(readOnly = true)
    public TenantSettingsDto getSettings(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        Map<String, String> configEntries = tenantConfigRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(TenantConfigEntry::getConfigKey, TenantConfigEntry::getConfigValue));

        return TenantSettingsDto.builder()
                .tenantId(tenantId)
                .configEntries(configEntries)
                .branding(TenantSettingsDto.Branding.builder()
                        .primaryColor("#1976D2")
                        .partnerName(tenant.getTenantName())
                        .logoUrl(null)
                        .build())
                .build();
    }

    @Transactional
    public void updateSettings(String tenantId, String configKey, String configValue, String updatedBy) {
        TenantConfigEntryId id = new TenantConfigEntryId(tenantId, configKey);
        TenantConfigEntry entry = tenantConfigRepository.findById(id)
                .map(existing -> {
                    existing.setConfigValue(configValue);
                    existing.setUpdatedBy(updatedBy);
                    existing.setUpdatedAt(Instant.now());
                    return existing;
                })
                .orElseGet(() -> TenantConfigEntry.builder()
                        .tenantId(tenantId)
                        .configKey(configKey)
                        .configValue(configValue)
                        .updatedBy(updatedBy)
                        .updatedAt(Instant.now())
                        .build());
        tenantConfigRepository.save(entry);
        log.info("Tenant config updated: tenant={} key={} by={}",
                sanitizeLog(tenantId), sanitizeLog(configKey), sanitizeLog(updatedBy));
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private static String sanitizeLog(String input) {
        if (input == null) return "null";
        return input.replaceAll("[\r\n\t]", "_");
    }

    // ─── Feature Flags ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Boolean> getFeatureFlags(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        return parseFeatureFlagsFromJson(tenant.getConfigJson());
    }

    private Map<String, Boolean> parseFeatureFlagsFromJson(String configJson) {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put("environment-module", true);
        defaults.put("esg-module", true);
        defaults.put("traffic-module", true);
        defaults.put("citizen-portal", true);
        defaults.put("ai-workflow", true);
        defaults.put("city-ops", true);
        if (configJson == null || configJson.isBlank()) return defaults;
        try {
            Map<String, Object> root = objectMapper.readValue(configJson, new TypeReference<>() {});
            Object featuresObj = root.get("features");
            if (!(featuresObj instanceof Map<?, ?> rawFeatures)) return defaults;
            Map<String, Boolean> result = new LinkedHashMap<>(defaults);
            rawFeatures.forEach((k, v) -> {
                if (k instanceof String key && v instanceof Map<?, ?> flagMap) {
                    Object enabledVal = flagMap.get("enabled");
                    boolean enabled = enabledVal instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(enabledVal));
                    result.put(key, enabled);
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse feature flags from config_json: {}", e.getMessage());
            return defaults;
        }
    }

    // ─── Tenant CRUD ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TenantSummaryDto> listAllTenants() {
        return tenantRepository.findAll().stream()
                .map(t -> TenantSummaryDto.builder()
                        .id(t.getId())
                        .tenantId(t.getTenantId())
                        .tenantName(t.getTenantName())
                        .tier(t.getTier())
                        .active(t.isActive())
                        .locationPath(t.getLocationPath())
                        .createdAt(t.getCreatedAt())
                        .build())
                .sorted(Comparator.comparing(TenantSummaryDto::tenantId))
                .toList();
    }

    @Transactional
    public TenantSummaryDto createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsByTenantId(request.tenantId())) {
            throw new IllegalArgumentException("Tenant already exists: " + request.tenantId());
        }
        Tenant tenant = new Tenant();
        tenant.setTenantId(request.tenantId());
        tenant.setTenantName(request.tenantName());
        tenant.setTier(request.tier() != null ? request.tier() : "T1");
        tenant.setLocationPath(request.locationPath());
        tenant.setActive(true);
        // Initialise feature flags: all enabled by default
        tenant.setConfigJson("{\"features\":{\"environment-module\":{\"enabled\":true},\"esg-module\":{\"enabled\":true},\"traffic-module\":{\"enabled\":true},\"citizen-portal\":{\"enabled\":true},\"ai-workflow\":{\"enabled\":true},\"city-ops\":{\"enabled\":true}}}");
        Tenant saved = tenantRepository.save(tenant);
        log.info("Tenant created: tenantId={} by system", sanitizeLog(saved.getTenantId()));
        return TenantSummaryDto.builder()
                .id(saved.getId())
                .tenantId(saved.getTenantId())
                .tenantName(saved.getTenantName())
                .tier(saved.getTier())
                .active(saved.isActive())
                .locationPath(saved.getLocationPath())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public void updateFeatureFlag(String tenantId, String featureKey, boolean enabled) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        try {
            Map<String, Object> config = tenant.getConfigJson() != null
                    ? objectMapper.readValue(tenant.getConfigJson(), new TypeReference<>() {})
                    : new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> features = (Map<String, Object>) config.computeIfAbsent("features", k -> new LinkedHashMap<>());
            features.put(featureKey, Map.of("enabled", enabled));
            tenant.setConfigJson(objectMapper.writeValueAsString(config));
            log.info("Feature flag updated: tenant={} feature={} enabled={}", sanitizeLog(tenantId), sanitizeLog(featureKey), enabled);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update feature flag for tenant: " + tenantId, e);
        }
    }
}
