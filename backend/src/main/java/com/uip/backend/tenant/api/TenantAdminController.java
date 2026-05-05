package com.uip.backend.tenant.api;

import com.uip.backend.tenant.api.dto.*;
import com.uip.backend.tenant.service.TenantAdminService;
import com.uip.backend.tenant.service.TenantUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Admin API", description = "Tenant administration endpoints")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;
    private final TenantUsageService tenantUsageService;

    @GetMapping("/{tenantId}/users")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    @Operation(summary = "List users in a tenant")
    public ResponseEntity<List<TenantUserDto>> listUsers(
            @PathVariable String tenantId,
            Authentication auth) {
        String effective = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        return ResponseEntity.ok(tenantAdminService.listUsers(effective));
    }

    @PostMapping("/{tenantId}/users/invite")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))")
    @Operation(summary = "Invite a user to tenant")
    public ResponseEntity<Void> inviteUser(
            @PathVariable String tenantId,
            @RequestBody @jakarta.validation.Valid InviteUserRequest request,
            Authentication auth) {
        String effective = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        tenantAdminService.inviteUser(effective, request, auth.getName());
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/{tenantId}/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))")
    @Operation(summary = "Update user role in tenant")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable String tenantId,
            @PathVariable UUID userId,
            @RequestBody @jakarta.validation.Valid UpdateRoleRequest request,
            Authentication auth) {
        String effective = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        tenantAdminService.updateUserRole(effective, userId, request.role());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{tenantId}/usage")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get tenant usage statistics")
    public ResponseEntity<TenantUsageDto> getUsage(
            @PathVariable String tenantId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            Authentication auth) {
        String effective = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        return ResponseEntity.ok(tenantUsageService.getUsage(effective, from, to));
    }

    @GetMapping("/{tenantId}/settings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get tenant settings")
    public ResponseEntity<TenantSettingsDto> getSettings(
            @PathVariable String tenantId,
            Authentication auth) {
        String effective = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        return ResponseEntity.ok(tenantAdminService.getSettings(effective));
    }

    @PutMapping("/{tenantId}/settings")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))")
    @Operation(summary = "Update tenant settings")
    public ResponseEntity<Void> updateSettings(
            @PathVariable String tenantId,
            @RequestBody @jakarta.validation.Valid UpdateSettingsRequest request,
            Authentication auth) {
        String effective = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        tenantAdminService.updateSettings(effective, request.configKey(), request.configValue(), auth.getName());
        return ResponseEntity.ok().build();
    }
}
