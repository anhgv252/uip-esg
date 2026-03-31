package com.uip.backend.traffic.api;

import com.uip.backend.traffic.api.dto.TrafficCountDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * S2-08 — Traffic API skeleton.
 * Returns mock data; full implementation in Sprint 3.
 */
@RestController
@RequestMapping("/api/v1/traffic")
@Tag(name = "Traffic", description = "Traffic counts and intersection data (Sprint 2 skeleton)")
public class TrafficController {

    @GetMapping("/counts")
    @Operation(summary = "Vehicle counts by intersection and time window (mock data)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TrafficCountDto>> getCounts(
            @RequestParam(required = false) String intersection,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        // Sprint 2 skeleton: return mock data
        List<TrafficCountDto> mock = List.of(
                TrafficCountDto.builder()
                        .intersectionId(intersection != null ? intersection : "INT-001")
                        .timestamp(Instant.now().minusSeconds(60))
                        .vehicleCount(120)
                        .avgSpeedKmh(35.4)
                        .congestionLevel("MEDIUM")
                        .build(),
                TrafficCountDto.builder()
                        .intersectionId(intersection != null ? intersection : "INT-002")
                        .timestamp(Instant.now().minusSeconds(120))
                        .vehicleCount(45)
                        .avgSpeedKmh(52.1)
                        .congestionLevel("LOW")
                        .build()
        );
        return ResponseEntity.ok(mock);
    }

    @GetMapping("/incidents")
    @Operation(summary = "Traffic incidents list (mock data)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getIncidents() {
        return ResponseEntity.ok(List.of(
                Map.of(
                        "id", "INC-001",
                        "type", "ACCIDENT",
                        "intersectionId", "INT-005",
                        "severity", "MEDIUM",
                        "status", "OPEN",
                        "occurredAt", Instant.now().minusSeconds(1800).toString()
                )
        ));
    }
}
