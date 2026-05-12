package com.uip.backend.building.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record BuildingCreateRequest(
    @NotBlank String buildingCode,
    @NotBlank String buildingName,
    String clusterId,
    @Min(1) Integer floorCount,
    @Positive Double totalAreaM2
) {}
