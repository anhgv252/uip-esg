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
     * ClickHouse table: energy_readings (tenant_id, building_id, kwh, demand_kw, power_factor, ts)
     */
    public List<BuildingEnergyBreakdown> aggregateByBuilding(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {

        String inClause = buildingIds.isEmpty() ? "1=1"
                : "building_id IN (" + "?,".repeat(buildingIds.size()).replaceAll(",$", "") + ")";

        String sql = """
                SELECT building_id,
                       sum(kwh)       AS total_kwh,
                       max(demand_kw) AS peak_demand_kw
                FROM energy_readings
                WHERE tenant_id = ?
                  AND %s
                  AND ts BETWEEN ? AND ?
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

    public double aggregatePowerFactor(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {

        String inClause = buildingIds.isEmpty() ? "1=1"
                : "building_id IN (" + "?,".repeat(buildingIds.size()).replaceAll(",$", "") + ")";

        String sql = """
                SELECT avg(power_factor) AS avg_pf
                FROM energy_readings
                WHERE tenant_id = ?
                  AND %s
                  AND ts BETWEEN ? AND ?
                """.formatted(inClause);

        Double result = jdbcTemplate.queryForObject(sql, Double.class,
                buildParams(tenantId, buildingIds, fromEpoch, toEpoch));
        // ClickHouse avg() trên empty set trả NaN, không phải null
        return (result == null || Double.isNaN(result)) ? 1.0 : result;
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
