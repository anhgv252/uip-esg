package com.uip.backend.tenant.api;

import com.uip.backend.tenant.api.dto.*;
import com.uip.backend.tenant.service.TenantAdminService;
import com.uip.backend.tenant.service.TenantUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Admin API", description = "Tenant administration endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;
    private final TenantUsageService tenantUsageService;

    @GetMapping("/{tenantId}/users")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    @Operation(summary = "List users in a tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN or TENANT_ADMIN role")
    })
    public ResponseEntity<List<TenantUserDto>> listUsers(
            @PathVariable String tenantId,
            Authentication auth) {
        String effective = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        return ResponseEntity.ok(tenantAdminService.listUsers(effective));
    }

    @PostMapping("/{tenantId}/users/invite")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))")
    @Operation(summary = "Invite a user to tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Invitation sent"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN or TENANT_ADMIN with tenant:admin authority")
    })
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
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User role updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN or TENANT_ADMIN with tenant:admin authority"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
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
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usage statistics returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN or TENANT_ADMIN role")
    })
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
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant settings returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN or TENANT_ADMIN role")
    })
    public ResponseEntity<TenantSettingsDto> getSettings(
            @PathVariable String tenantId,
            Authentication auth) {
        String effective = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        return ResponseEntity.ok(tenantAdminService.getSettings(effective));
    }

    @PutMapping("/{tenantId}/settings")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))")
    @Operation(summary = "Update tenant settings")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Settings updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN or TENANT_ADMIN with tenant:admin authority")
    })
    public ResponseEntity<Void> updateSettings(
            @PathVariable String tenantId,
            @RequestBody @jakarta.validation.Valid UpdateSettingsRequest request,
            Authentication auth) {
        String effective = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        tenantAdminService.updateSettings(effective, request.configKey(), request.configValue(), auth.getName());
        return ResponseEntity.ok().build();
    }

    // ─── Tenant CRUD (ADMIN only) ─────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all tenants")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    })
    public ResponseEntity<List<TenantSummaryDto>> listAllTenants() {
        return ResponseEntity.ok(tenantAdminService.listAllTenants());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tenant created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    })
    public ResponseEntity<TenantSummaryDto> createTenant(
            @RequestBody @jakarta.validation.Valid CreateTenantRequest request) {
        TenantSummaryDto created = tenantAdminService.createTenant(request);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/{tenantId}/features")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get feature flags for a tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Feature flags returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    })
    public ResponseEntity<Map<String, Boolean>> getFeatureFlags(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantAdminService.getFeatureFlags(tenantId));
    }

    @PutMapping("/{tenantId}/features")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enable or disable a feature flag for a tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Feature flag updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    })
    public ResponseEntity<Void> updateFeature(
            @PathVariable String tenantId,
            @RequestBody @jakarta.validation.Valid UpdateFeatureRequest request) {
        tenantAdminService.updateFeatureFlag(tenantId, request.featureKey(), request.enabled());
        return ResponseEntity.ok().build();
    }
}
