package com.uip.backend.dashboard.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Dashboard stats endpoint — aggregated counts across modules.
 *
 * Uses JdbcTemplate (not module repositories) to avoid module boundary violations
 * enforced by ModuleBoundaryArchTest.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Double energyKwh = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(value), 0) FROM esg.clean_metrics WHERE metric_type = 'ENERGY'" +
                " AND timestamp >= NOW() - INTERVAL '24 hours'",
                Double.class
        );
        Double prevEnergyKwh = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(value), 0) FROM esg.clean_metrics WHERE metric_type = 'ENERGY'" +
                " AND timestamp >= NOW() - INTERVAL '48 hours' AND timestamp < NOW() - INTERVAL '24 hours'",
                Double.class
        );
        double curr = energyKwh != null ? energyKwh : 0.0;
        double prev = prevEnergyKwh != null ? prevEnergyKwh : 0.0;
        String energyTrend = curr > prev * 1.05 ? "up" : curr < prev * 0.95 ? "down" : "stable";

        Double aqiRaw = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(aqi), 0) FROM environment.sensor_readings" +
                " WHERE timestamp >= NOW() - INTERVAL '1 hour' AND aqi IS NOT NULL",
                Double.class
        );
        int aqi = aqiRaw != null ? aqiRaw.intValue() : 0;
        String aqiLabel = aqi <= 50 ? "Tốt" : aqi <= 100 ? "Trung bình" : aqi <= 200 ? "Kém" : aqi <= 300 ? "Rất kém" : "Nguy hiểm";

        Long critCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alerts.alert_events WHERE status = 'OPEN' AND severity IN ('CRITICAL', 'EMERGENCY')",
                Long.class
        );
        Long warnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alerts.alert_events WHERE status = 'OPEN' AND severity = 'WARNING'",
                Long.class
        );
        long crit = critCount != null ? critCount : 0L;
        long warn = warnCount != null ? warnCount : 0L;
        int safetyScore = (int) Math.max(0, 100 - crit * 30 - warn * 10);
        String safetyStatus = crit > 0 ? "CRITICAL" : warn > 0 ? "WARNING" : "SAFE";

        Long onlineSensors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM environment.sensors WHERE is_active = true" +
                " AND last_seen_at >= NOW() - INTERVAL '5 minutes'",
                Long.class
        );
        Long totalSensors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM environment.sensors WHERE is_active = true",
                Long.class
        );
        Long openAlerts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alerts.alert_events WHERE status = 'OPEN'",
                Long.class
        );

        return ResponseEntity.ok(Map.of(
                "energyKwh", curr,
                "energyTrend", energyTrend,
                "safetyScore", safetyScore,
                "safetyStatus", safetyStatus,
                "aqi", aqi,
                "aqiLabel", aqiLabel,
                "activeAlerts", openAlerts != null ? openAlerts : 0L,
                "onlineSensors", onlineSensors != null ? onlineSensors : 0L,
                "totalSensors", totalSensors != null ? totalSensors : 0L
        ));
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getStats() {
        Long activeSensors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM environment.sensors WHERE is_active = true",
                Long.class
        );
        Long openAlerts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alerts.alert_events WHERE status = 'OPEN'",
                Long.class
        );
        Long totalBuildings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.buildings WHERE is_active = true",
                Long.class
        );

        return ResponseEntity.ok(Map.of(
                "activeSensors", activeSensors != null ? activeSensors : 0L,
                "openAlerts", openAlerts != null ? openAlerts : 0L,
                "totalBuildings", totalBuildings != null ? totalBuildings : 0L,
                "generatedAt", Instant.now().toString()
        ));
    }
}
