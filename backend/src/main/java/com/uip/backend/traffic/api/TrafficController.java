package com.uip.backend.traffic.api;

import com.uip.backend.traffic.api.dto.CongestionGeoJsonDto;
import com.uip.backend.traffic.api.dto.TrafficCountDto;
import com.uip.backend.traffic.api.dto.TrafficIncidentDto;
import com.uip.backend.traffic.service.TrafficService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * S3-03 — Full Traffic API Implementation
 * Real-time traffic counts, incidents, and congestion mapping
 */
@RestController
@RequestMapping("/api/v1/traffic")
@Tag(name = "Traffic", description = "Traffic counts, incidents, and real-time congestion mapping")
@RequiredArgsConstructor
public class TrafficController {
    
    private final TrafficService trafficService;
    
    /**
     * Get vehicle counts for an intersection within a time range
     * Default: last 24 hours
     */
    @GetMapping("/counts")
    @Operation(summary = "Vehicle counts by intersection and time window")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TrafficCountDto>> getCounts(
            @RequestParam(required = false, defaultValue = "INT-001") String intersection,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to) {
        
        LocalDateTime fromTime = from != null ? from : LocalDateTime.now().minusHours(24);
        LocalDateTime toTime = to != null ? to : LocalDateTime.now();
        
        List<TrafficCountDto> counts = trafficService.getTrafficCounts(intersection, fromTime, toTime);
        return ResponseEntity.ok(counts);
    }
    
    /**
     * Get all traffic incidents with optional status filter
     */
    @GetMapping("/incidents")
    @Operation(summary = "Get traffic incidents with status filter")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<TrafficIncidentDto>> getIncidents(
            @RequestParam(required = false, defaultValue = "OPEN") String status,
            Pageable pageable) {
        
        Page<TrafficIncidentDto> incidents = trafficService.getIncidents(status, pageable);
        return ResponseEntity.ok(incidents);
    }
    
    /**
     * Get congestion map as GeoJSON for visualization
     * Aggregates recent traffic data by intersection
     */
    @GetMapping("/congestion-map")
    @Operation(summary = "Get congestion map as GeoJSON FeatureCollection")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CongestionGeoJsonDto> getCongestionMap() {
        CongestionGeoJsonDto map = trafficService.getCongestionMap();
        return ResponseEntity.ok(map);
    }
    
    /**
     * Create a new traffic incident (admin/operator only)
     */
    @PostMapping("/incidents")
    @Operation(summary = "Create a new traffic incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<TrafficIncidentDto> createIncident(@RequestBody TrafficIncidentDto dto) {
        TrafficIncidentDto created = trafficService.createIncident(dto);
        return ResponseEntity.status(201).body(created);
    }
    
    /**
     * Update incident status
     */
    @PutMapping("/incidents/{id}/status")
    @Operation(summary = "Update traffic incident status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<TrafficIncidentDto> updateIncidentStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        TrafficIncidentDto updated = trafficService.updateIncidentStatus(id, status);
        return ResponseEntity.ok(updated);
    }
}
