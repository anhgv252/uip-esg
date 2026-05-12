package com.uip.backend.building.api;

import com.uip.backend.building.api.dto.BuildingCreateRequest;
import com.uip.backend.building.api.dto.BuildingResponse;
import com.uip.backend.building.service.BuildingClusterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
public class BuildingClusterController {

    private final BuildingClusterService service;

    @GetMapping
    public List<BuildingResponse> list(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        return service.findByTenant(tenantId)
            .stream()
            .map(BuildingResponse::from)
            .toList();
    }

    @GetMapping("/{buildingCode}")
    public BuildingResponse getByCode(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String buildingCode) {
        return BuildingResponse.from(service.findByCode(tenantId, buildingCode));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BuildingResponse create(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody BuildingCreateRequest request) {
        return BuildingResponse.from(service.create(request, tenantId));
    }

    @GetMapping("/clusters/{clusterId}")
    public List<BuildingResponse> listByCluster(@PathVariable String clusterId) {
        return service.findByCluster(clusterId)
            .stream()
            .map(BuildingResponse::from)
            .toList();
    }
}
