package com.uip.backend.esg.export;

import com.uip.backend.esg.domain.EsgMetric;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record EsgReportData(
        UUID reportId,
        String tenantId,
        int year,
        int quarter,
        Instant from,
        Instant to,
        Double energyTotal,
        Double waterTotal,
        Double carbonTotal,
        List<EsgMetric> energyMetrics,
        List<EsgMetric> waterMetrics,
        List<EsgMetric> carbonMetrics,
        // GRI 302-1 fields
        double energyIntensityKwhPerM2,
        Map<String, Double> buildingBreakdown,
        String dataQuality,
        // GRI 305-4 fields
        double co2EmissionsPerM2
) {}
