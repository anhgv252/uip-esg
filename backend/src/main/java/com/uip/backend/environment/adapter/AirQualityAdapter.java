package com.uip.backend.environment.adapter;

import com.uip.backend.common.spi.AirQualityPort;
import com.uip.backend.environment.repository.AirQualityReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * {@code environment}-side implementation of {@link AirQualityPort} (BUG-M5-009 fix).
 *
 * <p>Delegates PM2.5 aggregate queries to {@link AirQualityReadingRepository}. Lives in the
 * {@code environment} module so the {@code esg} module never touches the repository directly,
 * keeping the modular-monolith boundary intact.</p>
 */
@Component
@RequiredArgsConstructor
public class AirQualityAdapter implements AirQualityPort {

    private final AirQualityReadingRepository airQualityReadingRepository;

    @Override
    public Double findAveragePm25ByBuildingAndPeriod(String buildingId, Instant from, Instant to) {
        return airQualityReadingRepository.findAveragePm25ByBuildingAndPeriod(buildingId, from, to);
    }

    @Override
    public Double findAveragePm25ByPeriod(String tenantId, Instant start, Instant end) {
        return airQualityReadingRepository.findAveragePm25ByPeriod(tenantId, start, end);
    }
}
