package com.uip.backend.billing.api;

import com.uip.backend.billing.dto.BuildingRoiResponse;
import com.uip.backend.billing.dto.TenantRoiSummary;
import com.uip.backend.billing.service.RoiCalculationService;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * M5-3 T06: ROI Dashboard REST API.
 * 
 * Endpoints:
 *   - GET /api/v1/roi/building/{buildingId}   — Building ROI with cost breakdown
 *   - GET /api/v1/roi/summary                  — Tenant-level ROI summary (all buildings)
 * 
 * Security:
 *   - Building ROI: ROLE_ADMIN | ROLE_OPERATOR | ROLE_TENANT_ADMIN (can view own tenant's buildings)
 *   - Tenant summary: ROLE_ADMIN | ROLE_TENANT_ADMIN (cross-tenant admin)
 * 
 * Tenant isolation:
 *   - Enforced via TenantContext — users can only query their own tenant's data
 *   - Building endpoint returns 403 if buildingId belongs to another tenant
 */
@RestController
@RequestMapping("/api/v1/roi")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "ROI", description = "ROI Dashboard API")
@SecurityRequirement(name = "bearerAuth")
public class RoiController {

    private final RoiCalculationService roiCalculationService;

    /**
     * GET /api/v1/roi/building/{buildingId}
     * 
     * Returns ROI breakdown for a single building.
     * Month parameter defaults to current month if not specified.
     * 
     * Security: ROLE_ADMIN | ROLE_OPERATOR | ROLE_TENANT_ADMIN
     * Tenant isolation: returns 403 if buildingId belongs to another tenant
     * 
     * @param buildingId Building identifier
     * @param month Month in YYYY-MM format (optional, defaults to current month)
     * @return Building ROI with cost breakdown and savings
     */
    @GetMapping("/building/{buildingId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_TENANT_ADMIN')")
    @Operation(
        summary = "Get building ROI breakdown",
        description = "Returns ROI cost breakdown and savings for a single building. " +
                      "Month parameter defaults to current month if not specified. " +
                      "Requires ROLE_ADMIN, ROLE_OPERATOR, or ROLE_TENANT_ADMIN."
    )
    public ResponseEntity<BuildingRoiResponse> getBuildingRoi(
            @Parameter(description = "Building identifier", required = true)
            @PathVariable String buildingId,
            @Parameter(description = "Month in YYYY-MM format (optional, defaults to current month)", example = "2026-06")
            @RequestParam(required = false) String month,
            Authentication auth) {

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        log.info("ROI request for building {} (tenant {}) in month {} by user {}",
                buildingId, tenantId, month != null ? month : "current", auth.getName());

        BuildingRoiResponse response = roiCalculationService.calculateBuildingRoi(
                tenantId, buildingId, month);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/roi/summary
     * 
     * Returns tenant-level ROI summary (aggregate of all buildings).
     * Month parameter defaults to current month if not specified.
     * 
     * Security: ROLE_ADMIN | ROLE_TENANT_ADMIN
     * Tenant isolation: returns summary for authenticated user's tenant only
     * 
     * @param month Month in YYYY-MM format (optional, defaults to current month)
     * @return Tenant ROI summary with per-building breakdown
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TENANT_ADMIN')")
    @Operation(
        summary = "Get tenant ROI summary",
        description = "Returns tenant-level ROI summary aggregating all buildings. " +
                      "Month parameter defaults to current month if not specified. " +
                      "Requires ROLE_ADMIN or ROLE_TENANT_ADMIN."
    )
    public ResponseEntity<TenantRoiSummary> getTenantRoiSummary(
            @Parameter(description = "Month in YYYY-MM format (optional, defaults to current month)", example = "2026-06")
            @RequestParam(required = false) String month,
            Authentication auth) {

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        log.info("Tenant ROI summary request for tenant {} in month {} by user {}",
                tenantId, month != null ? month : "current", auth.getName());

        TenantRoiSummary response = roiCalculationService.calculateTenantRoiSummary(
                tenantId, month);

        return ResponseEntity.ok(response);
    }
}
