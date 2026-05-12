package com.uip.backend.building.repository;

import com.uip.backend.building.domain.BuildingCluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BuildingClusterRepository extends JpaRepository<BuildingCluster, UUID> {

    List<BuildingCluster> findByTenantIdAndIsActiveTrue(String tenantId);

    Optional<BuildingCluster> findByTenantIdAndBuildingCode(String tenantId, String buildingCode);

    List<BuildingCluster> findByClusterIdAndIsActiveTrue(String clusterId);

    boolean existsByTenantIdAndBuildingCode(String tenantId, String buildingCode);

    List<BuildingCluster> findByTenantIdInAndIsActiveTrue(List<String> tenantIds);
}
