package com.uip.analytics.api.dto;

import java.util.List;

public record EmissionsAggregateResponse(
    String tenantId,
    long fromEpoch,
    long toEpoch,
    double totalCo2Kg,
    List<TenantEmissionsBreakdown> buildings
) {
    public record TenantEmissionsBreakdown(
        String buildingId,
        double totalCo2Kg,
        double avgCo2PerHour
    ) {}
}
