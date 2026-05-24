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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        if (isCrossTenantViolation(request.tenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(energyService.aggregate(request));
    }

    @PostMapping("/emissions-aggregate")
    @Operation(summary = "Aggregate CO2 emissions across buildings",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'ANALYTICS_READ')")
    public ResponseEntity<EmissionsAggregateResponse> emissionsAggregate(
            @Valid @RequestBody EmissionsAggregateRequest request) {
        if (isCrossTenantViolation(request.tenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(emissionsService.aggregate(request));
    }

    @PostMapping("/aqi-trend")
    @Operation(summary = "Get AQI time-series trend across buildings",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'ANALYTICS_READ')")
    public ResponseEntity<AqiTrendResponse> aqiTrend(
            @Valid @RequestBody AqiTrendRequest request) {
        if (isCrossTenantViolation(request.tenantId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(aqiTrendService.getTrend(request));
    }

    /**
     * Returns true when the authenticated user's tenant_id (from JWT) does not match
     * the requested tenantId. Only enforced for Keycloak RS256 tokens (which carry
     * tenant_id in claims). Internal HMAC service tokens bypass this check.
     */
    private boolean isCrossTenantViolation(String requestedTenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object jwtTenantId = details.get("tenant_id");
            return jwtTenantId != null && !jwtTenantId.equals(requestedTenantId);
        }
        return false; // HMAC tokens (no details map) bypass cross-tenant check
    }
}
