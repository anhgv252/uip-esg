package com.uip.backend.esg.common;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public class CacheKeyBuilder {

    public String dashboardKey(String tenantId, String periodType, int year, int quarter) {
        Objects.requireNonNull(tenantId, "tenantId must not be null for cache key");
        return String.format("esg-dashboard:%s:%s:%d:Q%d", tenantId, periodType, year, quarter);
    }

    public String energyKey(String tenantId, Instant from, Instant to, String buildingId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null for cache key");
        String building = buildingId != null ? buildingId : "";
        return String.format("esg-dashboard:%s:ENERGY:%s:%s:%s",
                tenantId, building, from, to);
    }

    public String carbonKey(String tenantId, Instant from, Instant to) {
        Objects.requireNonNull(tenantId, "tenantId must not be null for cache key");
        return String.format("esg-dashboard:%s:CARBON:%s:%s", tenantId, from, to);
    }

    public String reportKey(String tenantId, String periodType, int year, int quarter) {
        Objects.requireNonNull(tenantId, "tenantId must not be null for cache key");
        return String.format("esg-report:%s:%s:%d:Q%d", tenantId, periodType, year, quarter);
    }

    public String reportStatusKey(String tenantId, java.util.UUID reportId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null for cache key");
        return String.format("esg-report:%s:status:%s", tenantId, reportId);
    }

    public String trendKey(String tenantId, Instant from, Instant to) {
        Objects.requireNonNull(tenantId, "tenantId must not be null for cache key");
        return String.format("esg-trend:%s:%s:%s", tenantId, from, to);
    }
}
