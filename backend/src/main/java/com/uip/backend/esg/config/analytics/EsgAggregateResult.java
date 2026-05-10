package com.uip.backend.esg.config.analytics;

import java.util.List;
import java.util.Map;

/** Kết quả aggregate từ AnalyticsPort. */
public record EsgAggregateResult(
    double totalKwh,
    double totalCo2Tonnes,
    Map<String, Double> kwhPerBuilding,
    List<String> buildingIds
) {}
