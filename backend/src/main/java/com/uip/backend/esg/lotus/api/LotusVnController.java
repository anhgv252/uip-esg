package com.uip.backend.esg.lotus.api;

import com.uip.backend.esg.lotus.domain.LotusVnReport;
import com.uip.backend.esg.lotus.service.LotusVnScoringService;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

/**
 * M5-4 T06: LOTUS VN Green Building Certification REST API.
 */
@RestController
@RequestMapping("/api/v1/esg/lotus")
@RequiredArgsConstructor
@Tag(name = "LOTUS VN", description = "LOTUS VN Green Building Certification Rating System")
@SecurityRequirement(name = "Bearer Authentication")
public class LotusVnController {

    private final LotusVnScoringService scoringService;

    @GetMapping("/buildings/{buildingId}/score")
    @Operation(summary = "Get LOTUS VN certification score for a building")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "LOTUS VN report returned"),
        @ApiResponse(responseCode = "400", description = "Invalid building ID or period"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Building not found")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LotusVnReport> getScore(
            @PathVariable String buildingId,
            @RequestParam(required = false, defaultValue = "2026-06") String period) {
        String tenantId = TenantContext.getCurrentTenant();
        YearMonth yearMonth = YearMonth.parse(period);
        LotusVnReport report = scoringService.score(buildingId, tenantId, yearMonth);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/buildings/{buildingId}/score")
    @Operation(summary = "Re-calculate LOTUS VN certification score (triggers recalculation)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Score recalculated"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PreAuthorize("hasAuthority('SCOPE_esg:admin')")
    public ResponseEntity<LotusVnReport> recalculateScore(
            @PathVariable String buildingId,
            @RequestParam(required = false, defaultValue = "2026-06") String period) {
        String tenantId = TenantContext.getCurrentTenant();
        YearMonth yearMonth = YearMonth.parse(period);
        LotusVnReport report = scoringService.score(buildingId, tenantId, yearMonth);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/buildings")
    @Operation(summary = "List all buildings with LOTUS VN certification status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List returned (placeholder)"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> listBuildings() {
        // TODO: Integrate with building metadata service
        return ResponseEntity.ok(List.of("BUILDING-01", "BUILDING-02"));
    }
}
