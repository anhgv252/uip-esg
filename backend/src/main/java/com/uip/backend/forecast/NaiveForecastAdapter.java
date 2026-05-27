package com.uip.backend.forecast;

import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.repository.EsgMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * In-process naive forecast adapter — rolling average fallback.
 *
 * ADR-032 D6: Uses EsgMetricRepository.findByTypeAndBuilding() → TimescaleDB.
 * Zero new dependencies. Activated when forecast-engine=naive.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "uip.capabilities.forecast-engine", havingValue = "naive")
@RequiredArgsConstructor
public class NaiveForecastAdapter implements ForecastPort {

    private final EsgMetricRepository esgMetricRepository;

    @Override
    public ForecastResult forecast(String tenantId, String buildingId, int horizonDays) {
        log.info("Naive forecast: tenant={}, building={}, horizon={}d", tenantId, buildingId, horizonDays);

        Instant to = Instant.now();
        Instant from = to.minus(90, ChronoUnit.DAYS);

        List<EsgMetric> metrics;
        try {
            metrics = esgMetricRepository.findByTypeAndBuilding(
                    tenantId, "ENERGY", buildingId, from, to
            );
        } catch (Exception e) {
            log.error("Naive forecast DB query failed: {}", e.getMessage());
            throw new ForecastServiceUnavailableException("Naive forecast data unavailable", e);
        }

        if (metrics.size() < 720) {
            log.warn("Insufficient data for naive forecast: {} points (< 30 days hourly)", metrics.size());
            return ForecastResult.insufficientData(tenantId, buildingId);
        }

        double avg = metrics.stream()
                .mapToDouble(EsgMetric::getValue)
                .average()
                .orElse(0.0);

        // Simple projection: repeat hourly average for horizon days
        List<ForecastPoint> points = new ArrayList<>();
        Instant start = to.truncatedTo(ChronoUnit.HOURS);
        for (int h = 0; h < horizonDays * 24; h++) {
            points.add(new ForecastPoint(
                    start.plus(h, ChronoUnit.HOURS),
                    null,
                    avg,
                    avg * 1.15,
                    avg * 0.85,
                    false
            ));
        }

        return new ForecastResult(tenantId, buildingId, "NAIVE", true, null, points, Instant.now());
    }
}
