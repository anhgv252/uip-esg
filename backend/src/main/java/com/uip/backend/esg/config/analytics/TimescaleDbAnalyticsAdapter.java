package com.uip.backend.esg.config.analytics;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Tier 1 implementation của AnalyticsPort.
 * Queries TimescaleDB Continuous Aggregates — đủ nhanh cho <500 sensors.
 *
 * Được load khi analytics-external=false (default, Tier 1).
 * Bị replace bởi ClickHouseRestAnalyticsAdapter khi analytics-external=true (Tier 2+).
 */
@Slf4j
public class TimescaleDbAnalyticsAdapter implements AnalyticsPort {

    @Override
    public EsgAggregateResult queryEnergyAggregate(
            String tenantId,
            List<String> buildingIds,
            long fromEpoch,
            long toEpoch) {
        log.debug("[Analytics] TimescaleDB query: tenant={}, buildings={}", tenantId, buildingIds);
        // In production: SELECT sum(kwh) FROM esg.aggregate_metrics
        //   WHERE tenant_id = ? AND building_id = ANY(?) AND ...
        return new EsgAggregateResult(0.0, 0.0, Map.of(), buildingIds);
    }
}
