package com.uip.backend.tenant.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class TenantUsageCrossSchemaRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Cross-schema read: environment.sensor_readings
     * Phase 1 exception — same DB instance, RLS enforced.
     * Phase 2: replace with gRPC call to environment-module.
     */
    public long countSensorReadings(String tenantId, Instant start, Instant end) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM environment.sensor_readings WHERE tenant_id = ? AND recorded_at >= ? AND recorded_at < ?",
                Long.class, tenantId, start, end);
        return count != null ? count : 0;
    }
}
