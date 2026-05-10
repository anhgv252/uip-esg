package com.uip.backend.esg.config.analytics;

import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.repository.EsgMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tier 1 implementation của AnalyticsPort.
 * Queries TimescaleDB Continuous Aggregates — đủ nhanh cho <500 sensors.
 *
 * Được load khi analytics-external=false (default, Tier 1).
 * Bị replace bởi ClickHouseRestAnalyticsAdapter khi analytics-external=true.
 */
@Slf4j
@RequiredArgsConstructor
public class TimescaleDbAnalyticsAdapter implements AnalyticsPort {

    private final EsgMetricRepository metricRepository;

    @Override
    public EsgAggregateResult queryEnergyAggregate(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {

        Instant from = Instant.ofEpochSecond(fromEpoch);
        Instant to   = Instant.ofEpochSecond(toEpoch);

        log.debug("[Analytics-T1] TimescaleDB energy query: tenant={} from={} to={}", tenantId, from, to);

        // Dùng continuous aggregate nếu có, fallback sang raw scan
        Double totalKwh = metricRepository.sumByTypeAndRangeFast(tenantId, "ENERGY", from, to);
        if (totalKwh == null) {
            totalKwh = metricRepository.sumByTypeAndRange(tenantId, "ENERGY", from, to);
        }

        Map<String, Double> kwhPerBuilding = buildingIds.isEmpty()
                ? Map.of()
                : buildingIds.stream().collect(Collectors.toMap(
                    bid -> bid,
                    bid -> {
                        List<EsgMetric> rows = metricRepository.findByTypeAndBuilding(
                                tenantId, "ENERGY", bid, from, to);
                        return rows.stream().mapToDouble(EsgMetric::getValue).sum();
                    }
                  ));

        // CO2 estimate: 0.5 kg/kWh → 0.0005 tonnes/kWh (grid emission factor, Vietnam avg)
        double totalCo2 = totalKwh != null ? totalKwh * 0.0005 : 0.0;

        return new EsgAggregateResult(
                totalKwh != null ? totalKwh : 0.0,
                totalCo2,
                kwhPerBuilding,
                buildingIds);
    }
}
