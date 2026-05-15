package com.uip.analytics.api;

import com.uip.analytics.api.dto.*;
import com.uip.analytics.service.AqiTrendService;
import com.uip.analytics.service.EmissionsAggregateService;
import com.uip.analytics.service.EnergyAggregateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Cross-building OLAP queries — Tier 2+")
public class AnalyticsController {

    private final EnergyAggregateService energyService;
    private final EmissionsAggregateService emissionsService;
    private final AqiTrendService aqiTrendService;

    @PostMapping("/energy-aggregate")
    @Operation(summary = "Aggregate energy across buildings",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'ANALYTICS_READ')")
    public ResponseEntity<EnergyAggregateResponse> energyAggregate(
            @Valid @RequestBody EnergyAggregateRequest request) {
        return ResponseEntity.ok(energyService.aggregate(request));
    }

    @PostMapping("/emissions-aggregate")
    @Operation(summary = "Aggregate CO2 emissions across buildings",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'ANALYTICS_READ')")
    public ResponseEntity<EmissionsAggregateResponse> emissionsAggregate(
            @Valid @RequestBody EmissionsAggregateRequest request) {
        return ResponseEntity.ok(emissionsService.aggregate(request));
    }

    @PostMapping("/aqi-trend")
    @Operation(summary = "Get AQI time-series trend across buildings",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'ANALYTICS_READ')")
    public ResponseEntity<AqiTrendResponse> aqiTrend(
            @Valid @RequestBody AqiTrendRequest request) {
        return ResponseEntity.ok(aqiTrendService.getTrend(request));
    }
}
