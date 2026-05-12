package com.uip.backend.building.service;

import com.uip.backend.building.api.dto.CrossBuildingAggregationRequest;
import com.uip.backend.building.api.dto.CrossBuildingAggregationResult;
import com.uip.backend.building.domain.BuildingCluster;
import com.uip.backend.building.repository.BuildingClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CrossBuildingAggregationService {

    private final JdbcTemplate jdbcTemplate;
    private final BuildingClusterRepository buildingRepository;

    /**
     * Cross-building aggregation từ esg.clean_metrics.
     * building_id trong clean_metrics là VARCHAR → match với building_code.
     * Anti-pattern: KHÔNG JOIN cross-schema; KHÔNG query không có time range bound.
     */
    public List<CrossBuildingAggregationResult> aggregate(
            String tenantId,
            CrossBuildingAggregationRequest request) {

        // Build lookup map: buildingCode → BuildingCluster
        Map<String, BuildingCluster> buildingMap = buildingRepository
            .findByTenantIdAndIsActiveTrue(tenantId)
            .stream()
            .filter(b -> request.buildingCodes().contains(b.getBuildingCode()))
            .collect(Collectors.toMap(BuildingCluster::getBuildingCode, b -> b));

        if (buildingMap.isEmpty()) {
            log.warn("[CrossBuilding] No active buildings found for tenant={}, codes={}",
                tenantId, request.buildingCodes());
            return List.of();
        }

        // Native SQL — esg schema, time-bounded, tenant-scoped
        String sql = """
            SELECT
                m.building_id                           AS building_code,
                SUM(m.value)                            AS total_value,
                AVG(m.value)                            AS avg_value,
                COUNT(*)                                AS data_points,
                MAX(m.unit)                             AS unit
            FROM esg.clean_metrics m
            WHERE m.tenant_id   = ?
              AND m.metric_type = ?
              AND m.timestamp   BETWEEN ? AND ?
              AND m.building_id = ANY(?)
            GROUP BY m.building_id
            ORDER BY m.building_id
            """;

        // PostgreSQL ANY(?) cần java.sql.Array — JdbcTemplate không auto-convert String[]
        return jdbcTemplate.execute(
            (java.sql.Connection conn) -> {
                java.sql.Array codesArray = conn.createArrayOf(
                    "varchar", request.buildingCodes().toArray(new String[0]));
                try (var ps = conn.prepareStatement(sql)) {
                    ps.setString(1, tenantId);
                    ps.setString(2, request.metricType());
                    ps.setTimestamp(3, Timestamp.from(request.from().toInstant()));
                    ps.setTimestamp(4, Timestamp.from(request.to().toInstant()));
                    ps.setArray(5, codesArray);
                    var rs = ps.executeQuery();
                    java.util.List<CrossBuildingAggregationResult> results = new java.util.ArrayList<>();
                    while (rs.next()) {
                        String code = rs.getString("building_code");
                        BuildingCluster bc = buildingMap.get(code);
                        results.add(new CrossBuildingAggregationResult(
                            code,
                            bc != null ? bc.getBuildingName() : code,
                            rs.getDouble("total_value"),
                            rs.getDouble("avg_value"),
                            rs.getLong("data_points"),
                            rs.getString("unit")
                        ));
                    }
                    return results;
                }
            }
        );
    }
}
