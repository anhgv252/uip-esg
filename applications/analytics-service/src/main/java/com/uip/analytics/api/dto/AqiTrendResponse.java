package com.uip.analytics.api.dto;

import java.util.List;

public record AqiTrendResponse(
    String tenantId,
    List<AqiDataPoint> dataPoints
) {
    public record AqiDataPoint(
        String buildingId,
        long timestampEpoch,
        double avgAqi,
        double maxAqi
    ) {}
}
