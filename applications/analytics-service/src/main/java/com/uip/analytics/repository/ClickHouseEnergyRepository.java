package com.uip.analytics.repository;

import com.uip.analytics.api.dto.AqiTrendResponse.AqiDataPoint;
import com.uip.analytics.api.dto.EmissionsAggregateResponse.TenantEmissionsBreakdown;
import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import com.uip.analytics.security.RowPolicyEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ClickHouseEnergyRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowPolicyEngine rowPolicyEngine;

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

            // ADR-047: run on the tenant-scoped connection so the CH RowPolicy (L2)
            // enforces isolation in addition to the WHERE clause (L1) above.
            return rowPolicyEngine.executeWithTenant(tenantId, conn -> {
                List<BuildingEnergyBreakdown> out = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bindParams(ps, tenantId, buildingIds, fromEpoch, toEpoch);
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(new BuildingEnergyBreakdown(
                                    rs.getString("building_id"),
                                    rs.getDouble("total_kwh"),
                                    rs.getDouble("peak_demand_kw")
                            ));
                        }
                    }
                }
                return out;
            });
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

            return rowPolicyEngine.executeWithTenant(tenantId, conn -> {
                List<TenantEmissionsBreakdown> out = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bindParams(ps, tenantId, buildingIds, fromEpoch, toEpoch);
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(new TenantEmissionsBreakdown(
                                    rs.getString("building_id"),
                                    rs.getDouble("total_co2_kg"),
                                    rs.getDouble("avg_co2_per_hour")
                            ));
                        }
                    }
                }
                return out;
            });
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

            return rowPolicyEngine.executeWithTenant(tenantId, conn -> {
                List<AqiDataPoint> out = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bindParams(ps, tenantId, buildingIds, fromEpoch, toEpoch);
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(new AqiDataPoint(
                                    rs.getString("building_id"),
                                    rs.getLong("ts_hour"),
                                    rs.getDouble("avg_aqi"),
                                    rs.getDouble("max_aqi")
                            ));
                        }
                    }
                }
                return out;
            });
        });
    }

    public double aggregatePowerFactor(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {
        return 1.0;
    }

    /**
     * Bind query params in the order the SQL expects them:
     * {@code tenant_id, [building_id...], fromEpoch, toEpoch}.
     */
    private void bindParams(
            PreparedStatement ps, String tenantId, List<String> buildingIds,
            long fromEpoch, long toEpoch) throws java.sql.SQLException {
        int i = 1;
        ps.setString(i++, tenantId);
        for (String buildingId : buildingIds) {
            ps.setString(i++, buildingId);
        }
        ps.setLong(i++, fromEpoch);
        ps.setLong(i, toEpoch);
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
