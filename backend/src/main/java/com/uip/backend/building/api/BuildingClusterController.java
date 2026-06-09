package com.uip.backend.building.api;

import com.uip.backend.building.api.dto.BuildingCreateRequest;
import com.uip.backend.building.api.dto.BuildingResponse;
import com.uip.backend.building.service.BuildingClusterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
@Tag(name = "Buildings", description = "Building cluster management and safety queries")
@SecurityRequirement(name = "Bearer Authentication")
public class BuildingClusterController {

    private final BuildingClusterService service;

    @GetMapping
    @Operation(summary = "List buildings for current tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Building list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public List<BuildingResponse> list(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return service.findByTenant(tenantId)
            .stream()
            .map(BuildingResponse::from)
            .toList();
    }

    @GetMapping("/{buildingCode}")
    @Operation(summary = "Get building by code")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Building returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Building not found")
    })
    public BuildingResponse getByCode(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String buildingCode) {
        return BuildingResponse.from(service.findByCode(tenantId, buildingCode));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new building")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Building created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public BuildingResponse create(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody BuildingCreateRequest request) {
        return BuildingResponse.from(service.create(request, tenantId));
    }

    @GetMapping("/clusters/{clusterId}")
    @Operation(summary = "List buildings by cluster ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Buildings in cluster returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public List<BuildingResponse> listByCluster(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String clusterId) {
        return service.findByCluster(clusterId)
            .stream()
            .filter(b -> tenantId.equals(b.getTenantId()))
            .map(BuildingResponse::from)
            .toList();
    }
}
