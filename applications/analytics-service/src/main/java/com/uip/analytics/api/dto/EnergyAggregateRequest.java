package com.uip.analytics.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record EnergyAggregateRequest(
    @NotBlank  String tenantId,
               List<String> buildingIds,  // empty = tất cả buildings của tenant
    @NotNull @Positive long fromEpoch,
    @NotNull @Positive long toEpoch
) {}
