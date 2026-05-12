package com.uip.analytics.api;

import com.uip.analytics.api.dto.EnergyAggregateRequest;
import com.uip.analytics.api.dto.EnergyAggregateResponse;
import com.uip.analytics.service.EnergyAggregateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Cross-building OLAP queries — Tier 2+")
public class AnalyticsController {

    private final EnergyAggregateService service;

    @PostMapping("/energy-aggregate")
    @Operation(summary = "Aggregate energy across buildings",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'ANALYTICS_READ')")
    public ResponseEntity<EnergyAggregateResponse> energyAggregate(
            @Valid @RequestBody EnergyAggregateRequest request) {
        return ResponseEntity.ok(service.aggregate(request));
    }
}
