package com.uip.backend.esg.iso37120.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M5-4 T10: ISO 37120:2018 City services and quality of life indicator report.
 * Contains 15 urban sustainability indicators across 5 categories:
 * - Energy (E1, E2)
 * - Environment (Env1, Env2, Env3)
 * - Transport (T1)
 * - Waste (W1)
 * - Governance (G1, G2)
 */
public record Iso37120Report(
    String cityId,
    int year,
    List<Iso37120Indicator> indicators,
    Instant calculatedAt
) {
    /**
     * Group indicators by category for dashboard display.
     */
    public Map<String, List<Iso37120Indicator>> groupedByCategory() {
        return indicators.stream()
            .collect(Collectors.groupingBy(Iso37120Indicator::category));
    }

    /**
     * Count available indicators (those with real data).
     */
    public long availableCount() {
        return indicators.stream()
            .filter(Iso37120Indicator::dataAvailable)
            .count();
    }
}
