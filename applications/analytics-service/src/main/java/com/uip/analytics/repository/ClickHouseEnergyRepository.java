package com.uip.analytics.repository;

import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ClickHouseEnergyRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Aggregate energy metrics per building over a time range.
     * ClickHouse table: analytics.esg_readings (tenant_id, building_id, source_id, metric_type, value, unit, recorded_at)
     * Filters metric_type = 'ENERGY' to get energy-specific readings.
     */
    public List<BuildingEnergyBreakdown> aggregateByBuilding(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {

        String inClause = buildingIds.isEmpty() ? "1=1"
                : "building_id IN (" + "?,".repeat(buildingIds.size()).replaceAll(",$", "") + ")";

        String sql = """
                SELECT building_id,
                       sum(value)       AS total_kwh,
                       max(value)       AS peak_demand_kw
                FROM esg_readings
                WHERE tenant_id = ?
                  AND metric_type = 'ENERGY'
                  AND %s
                  AND recorded_at >= fromUnixTimestamp(?)
                  AND recorded_at <= fromUnixTimestamp(?)
                GROUP BY building_id
                ORDER BY building_id
                """.formatted(inClause);

        Object[] params = buildParams(tenantId, buildingIds, fromEpoch, toEpoch);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new BuildingEnergyBreakdown(
                rs.getString("building_id"),
                rs.getDouble("total_kwh"),
                rs.getDouble("peak_demand_kw")
        ));
    }

    /**
     * Average "power factor" approximation from energy readings.
     * Since esg_readings only has generic value, we return 1.0 (unity PF)
     * when no specific power_factor data exists.
     * TODO: Add dedicated power_factor metric type in Sprint 3.
     */
    public double aggregatePowerFactor(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {
        return 1.0;
    }

    private Object[] buildParams(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {

        Object[] params = new Object[3 + buildingIds.size()];
        params[0] = tenantId;
        for (int i = 0; i < buildingIds.size(); i++) {
            params[1 + i] = buildingIds.get(i);
        }
        params[1 + buildingIds.size()] = fromEpoch;
        params[2 + buildingIds.size()] = toEpoch;
        return params;
    }
}
