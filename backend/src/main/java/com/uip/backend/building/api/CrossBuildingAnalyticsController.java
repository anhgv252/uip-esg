package com.uip.backend.building.api;

import com.uip.backend.building.api.dto.CrossBuildingAggregationRequest;
import com.uip.backend.building.api.dto.CrossBuildingAggregationResult;
import com.uip.backend.building.service.BuildingClusterService;
import com.uip.backend.building.service.CrossBuildingAggregationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics/cross-building")
@RequiredArgsConstructor
public class CrossBuildingAnalyticsController {

    private final CrossBuildingAggregationService aggregationService;
    private final BuildingClusterService buildingService;

    @PostMapping("/aggregate")
    public List<CrossBuildingAggregationResult> aggregate(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody CrossBuildingAggregationRequest request) {
        // Validate building ownership trước query (BR-003)
        buildingService.validateOwnership(tenantId, request.buildingCodes());
        return aggregationService.aggregate(tenantId, request);
    }
}
