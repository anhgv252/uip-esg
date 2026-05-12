package com.uip.backend.building.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record CrossBuildingAggregationRequest(
    @NotEmpty @Size(max = 5, message = "Max 5 buildings per request") List<String> buildingCodes,
    @NotBlank String metricType,
    @NotNull OffsetDateTime from,
    @NotNull OffsetDateTime to
) {}
