package com.uip.analytics.api.dto;

import java.util.List;

public record EnergyAggregateResponse(
    String tenantId,
    long fromEpoch,
    long toEpoch,
    double totalKwh,
    double peakDemandKw,
    double averagePowerFactor,
    List<BuildingEnergyBreakdown> buildings
) {
    public record BuildingEnergyBreakdown(
        String buildingId,
        double totalKwh,
        double peakDemandKw
    ) {}
}
