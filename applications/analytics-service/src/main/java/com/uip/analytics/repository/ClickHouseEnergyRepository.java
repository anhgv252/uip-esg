package com.uip.analytics.repository;

import com.uip.analytics.api.dto.AqiTrendResponse.AqiDataPoint;
import com.uip.analytics.api.dto.EmissionsAggregateResponse.TenantEmissionsBreakdown;
import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ClickHouseEnergyRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<BuildingEnergyBreakdown> aggregateByBuilding(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {
        return safeQuery(() -> {
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

            return jdbcTemplate.query(sql, buildParams(tenantId, buildingIds, fromEpoch, toEpoch),
                    (rs, rowNum) -> new BuildingEnergyBreakdown(
                            rs.getString("building_id"),
                            rs.getDouble("total_kwh"),
                            rs.getDouble("peak_demand_kw")
                    ));
        });
    }

    public List<TenantEmissionsBreakdown> aggregateEmissionsByBuilding(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {
        return safeQuery(() -> {
            String inClause = buildingIds.isEmpty() ? "1=1"
                    : "building_id IN (" + "?,".repeat(buildingIds.size()).replaceAll(",$", "") + ")";

            String sql = """
                    SELECT building_id,
                           sum(value)      AS total_co2_kg,
                           avg(value)      AS avg_co2_per_hour
                    FROM esg_readings
                    WHERE tenant_id = ?
                      AND metric_type = 'CARBON'
                      AND %s
                      AND recorded_at >= fromUnixTimestamp(?)
                      AND recorded_at <= fromUnixTimestamp(?)
                    GROUP BY building_id
                    ORDER BY building_id
                    """.formatted(inClause);

            return jdbcTemplate.query(sql, buildParams(tenantId, buildingIds, fromEpoch, toEpoch),
                    (rs, rowNum) -> new TenantEmissionsBreakdown(
                            rs.getString("building_id"),
                            rs.getDouble("total_co2_kg"),
                            rs.getDouble("avg_co2_per_hour")
                    ));
        });
    }

    public List<AqiDataPoint> getAqiTrend(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {
        return safeQuery(() -> {
            String inClause = buildingIds.isEmpty() ? "1=1"
                    : "building_id IN (" + "?,".repeat(buildingIds.size()).replaceAll(",$", "") + ")";

            String sql = """
                    SELECT building_id,
                           toUnixTimestamp(toStartOfHour(recorded_at)) AS ts_hour,
                           avg(value)  AS avg_aqi,
                           max(value)  AS max_aqi
                    FROM esg_readings
                    WHERE tenant_id = ?
                      AND metric_type = 'AIR_QUALITY'
                      AND %s
                      AND recorded_at >= fromUnixTimestamp(?)
                      AND recorded_at <= fromUnixTimestamp(?)
                    GROUP BY building_id, toStartOfHour(recorded_at)
                    ORDER BY building_id, ts_hour
                    """.formatted(inClause);

            return jdbcTemplate.query(sql, buildParams(tenantId, buildingIds, fromEpoch, toEpoch),
                    (rs, rowNum) -> new AqiDataPoint(
                            rs.getString("building_id"),
                            rs.getLong("ts_hour"),
                            rs.getDouble("avg_aqi"),
                            rs.getDouble("max_aqi")
                    ));
        });
    }

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

    @FunctionalInterface
    private interface QuerySupplier<T> {
        List<T> get() throws Exception;
    }

    private <T> List<T> safeQuery(QuerySupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("ClickHouse query failed: {} — returning empty result", e.getMessage());
            return Collections.emptyList();
        }
    }
}
