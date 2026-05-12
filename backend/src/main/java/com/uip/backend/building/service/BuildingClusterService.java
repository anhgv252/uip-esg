package com.uip.backend.building.service;

import com.uip.backend.building.api.dto.BuildingCreateRequest;
import com.uip.backend.building.domain.BuildingCluster;
import com.uip.backend.building.repository.BuildingClusterRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingClusterService {

    private final BuildingClusterRepository repository;

    public List<BuildingCluster> findByTenant(String tenantId) {
        return repository.findByTenantIdAndIsActiveTrue(tenantId);
    }

    public BuildingCluster findByCode(String tenantId, String buildingCode) {
        return repository.findByTenantIdAndBuildingCode(tenantId, buildingCode)
            .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
            .orElseThrow(() -> new EntityNotFoundException(
                "Building not found: " + buildingCode + " for tenant: " + tenantId));
    }

    public List<BuildingCluster> findByCluster(String clusterId) {
        return repository.findByClusterIdAndIsActiveTrue(clusterId);
    }

    /**
     * Validate rằng tất cả buildingCodes thuộc tenantId — service-layer enforcement (BR-003).
     */
    public void validateOwnership(String tenantId, List<String> buildingCodes) {
        List<String> ownedCodes = repository.findByTenantIdAndIsActiveTrue(tenantId)
            .stream()
            .map(BuildingCluster::getBuildingCode)
            .toList();
        List<String> unauthorized = buildingCodes.stream()
            .filter(code -> !ownedCodes.contains(code))
            .toList();
        if (!unauthorized.isEmpty()) {
            throw new AccessDeniedException(
                "Buildings not owned by tenant " + tenantId + ": " + unauthorized);
        }
    }

    @Transactional
    public BuildingCluster create(BuildingCreateRequest request, String tenantId) {
        if (repository.existsByTenantIdAndBuildingCode(tenantId, request.buildingCode())) {
            throw new IllegalArgumentException(
                "Building code already exists: " + request.buildingCode());
        }
        BuildingCluster building = BuildingCluster.builder()
            .buildingCode(request.buildingCode())
            .buildingName(request.buildingName())
            .tenantId(tenantId)
            .clusterId(request.clusterId())
            .floorCount(request.floorCount() != null ? request.floorCount() : 1)
            .totalAreaM2(request.totalAreaM2())
            .build();
        return repository.save(building);
    }
}
