package com.uip.backend.building.api.dto;

public record CrossBuildingAggregationResult(
    String buildingCode,
    String buildingName,
    double totalValue,
    double avgValue,
    long dataPoints,
    String unit
) {}
