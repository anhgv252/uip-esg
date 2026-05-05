package com.uip.backend.esg.export;

import com.uip.backend.esg.domain.EsgMetric;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
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
        List<EsgMetric> carbonMetrics
) {}
