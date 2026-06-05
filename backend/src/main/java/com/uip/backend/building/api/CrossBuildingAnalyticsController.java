package com.uip.backend.building.api;

import com.uip.backend.building.api.dto.CrossBuildingAggregationRequest;
import com.uip.backend.building.api.dto.CrossBuildingAggregationResult;
import com.uip.backend.building.service.BuildingClusterService;
import com.uip.backend.building.service.CrossBuildingAggregationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics/cross-building")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Cross-building analytics and aggregation")
@SecurityRequirement(name = "Bearer Authentication")
public class CrossBuildingAnalyticsController {

    private final CrossBuildingAggregationService aggregationService;
    private final BuildingClusterService buildingService;

    @PostMapping("/aggregate")
    @Operation(summary = "Aggregate metrics across buildings")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Aggregation result returned"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "403", description = "Building ownership validation failed")
    })
    public List<CrossBuildingAggregationResult> aggregate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody CrossBuildingAggregationRequest request) {
        // Validate building ownership trước query (BR-003)
        buildingService.validateOwnership(tenantId, request.buildingCodes());
        return aggregationService.aggregate(tenantId, request);
    }
}
