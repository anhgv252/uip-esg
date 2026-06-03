package com.uip.backend.safety.controller;

import com.uip.backend.safety.dto.SafetyScoreResponse;
import com.uip.backend.safety.dto.VibrationReadingResponse;
import com.uip.backend.safety.service.BuildingSafetyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
@Tag(name = "Building Safety", description = "Structural safety scores and vibration sensor readings")
public class BuildingSafetyController {

    private final BuildingSafetyService buildingSafetyService;

    /**
     * Returns the current safety score for a building (0-100).
     * Cached in Redis for 5 minutes; evicted on new structural alert (BR-010 safe).
     */
    @GetMapping("/{id}/safety")
    @Operation(summary = "Building safety score — 0-100, zones: SAFE/WARNING/CRITICAL/OFFLINE")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SafetyScoreResponse> getSafetyScore(@PathVariable("id") String buildingId) {
        return ResponseEntity.ok(
                SafetyScoreResponse.from(buildingSafetyService.getSafetyScore(buildingId)));
    }

    /**
     * Returns structural sensor readings for the 24h trend chart.
     *
     * @param buildingId target building
     * @param sensorType STRUCTURAL_VIBRATION | STRUCTURAL_TILT | STRUCTURAL_CRACK
     * @param range      time range — 24h (default), 7d, 30d
     */
    @GetMapping("/{id}/vibration/readings")
    @Operation(summary = "Structural sensor readings — vibration/tilt/crack time series")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<VibrationReadingResponse>> getVibrationReadings(
            @PathVariable("id")                                String buildingId,
            @RequestParam(defaultValue = "STRUCTURAL_VIBRATION") String sensorType,
            @RequestParam(defaultValue = "24h")                  String range) {

        return ResponseEntity.ok(
                buildingSafetyService.getVibrationReadings(buildingId, sensorType, range));
    }
}
