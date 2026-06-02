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
