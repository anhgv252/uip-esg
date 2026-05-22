package com.uip.backend.tenant.service;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAdminService {

    private final TenantRepository tenantRepository;
    private final TenantConfigRepository tenantConfigRepository;
    private final AppUserRepository appUserRepository;
    private final InviteService inviteService;

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
}
