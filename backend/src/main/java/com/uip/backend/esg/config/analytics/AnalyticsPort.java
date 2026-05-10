package com.uip.backend.esg.config.analytics;

/**
 * Port Interface cho analytics queries (ADR-011).
 *
 * Tier 1 (monolith): TimescaleDbAnalyticsAdapter implements this.
 * Tier 2+ (extracted): ClickHouseRestAnalyticsAdapter implements this,
 *   delegate sang analytics-service qua HTTP.
 *
 * Business code chỉ biết interface này — không đổi khi swap implementation.
 */
public interface AnalyticsPort {

    /**
     * Tổng energy consumption theo building, trong khoảng thời gian.
     * @param tenantId  tenant context (RLS enforced)
     * @param buildingIds  danh sách building IDs (empty = all tenant buildings)
     * @param fromEpoch  start time (epoch seconds)
     * @param toEpoch    end time (epoch seconds)
     */
    EsgAggregateResult queryEnergyAggregate(
            String tenantId,
            java.util.List<String> buildingIds,
            long fromEpoch,
            long toEpoch);
}
