package com.uip.backend.building.api.dto;

import com.uip.backend.building.domain.BuildingCluster;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BuildingResponse(
    UUID id,
    String buildingCode,
    String buildingName,
    String tenantId,
    String clusterId,
    Integer floorCount,
    Double totalAreaM2,
    boolean isActive,
    OffsetDateTime createdAt
) {
    public static BuildingResponse from(BuildingCluster b) {
        return new BuildingResponse(
            b.getId(),
            b.getBuildingCode(),
            b.getBuildingName(),
            b.getTenantId(),
            b.getClusterId(),
            b.getFloorCount(),
            b.getTotalAreaM2(),
            Boolean.TRUE.equals(b.getIsActive()),
            b.getCreatedAt()
        );
    }
}
