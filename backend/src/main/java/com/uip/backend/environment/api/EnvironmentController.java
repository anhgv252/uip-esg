package com.uip.backend.environment.api;

import com.uip.backend.environment.api.dto.AqiResponseDto;
import com.uip.backend.environment.api.dto.SensorDto;
import com.uip.backend.environment.api.dto.SensorReadingDto;
import com.uip.backend.environment.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/environment")
@RequiredArgsConstructor
@Tag(name = "Environment", description = "Sensor readings, AQI data and environmental metrics")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @GetMapping("/sensors")
    @Operation(summary = "List all active sensors with online/offline status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SensorDto>> listSensors() {
        return ResponseEntity.ok(environmentService.listSensors());
    }

    @GetMapping("/sensors/{sensorId}/readings")
    @Operation(summary = "Time-series readings for a sensor")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SensorReadingDto>> getReadings(
            @PathVariable String sensorId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "100") int limit) {

        Instant effectiveTo   = to   != null ? to   : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(24, ChronoUnit.HOURS);
        return ResponseEntity.ok(environmentService.getReadings(sensorId, effectiveFrom, effectiveTo, limit));
    }

    @GetMapping("/aqi/current")
    @Operation(summary = "Current AQI per sensor (latest reading)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AqiResponseDto>> getCurrentAqi() {
        return ResponseEntity.ok(environmentService.getCurrentAqi());
    }

    @GetMapping("/aqi/history")
    @Operation(summary = "AQI history — optionally filtered by district and period (24h/7d/30d)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AqiResponseDto>> getAqiHistory(
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "24h") String period) {
        return ResponseEntity.ok(environmentService.getAqiHistory(district, period));
    }
}
