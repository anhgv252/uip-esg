package com.uip.backend.esg.iso37120.api;

import com.uip.backend.esg.iso37120.domain.Iso37120Report;
import com.uip.backend.esg.iso37120.service.Iso37120IndicatorEngine;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.List;
import java.util.Map;

/**
 * M5-4 T10: ISO 37120:2018 City services and quality of life indicators REST API.
 */
@RestController
@RequestMapping("/api/v1/esg/iso37120")
@RequiredArgsConstructor
@Tag(name = "ISO 37120", description = "ISO 37120:2018 City services and quality of life indicators")
@SecurityRequirement(name = "Bearer Authentication")
public class Iso37120Controller {

    private final Iso37120IndicatorEngine indicatorEngine;

    @GetMapping("/report")
    @Operation(summary = "Get ISO 37120 indicator report for a city and year")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "ISO 37120 report returned"),
        @ApiResponse(responseCode = "400", description = "Invalid year parameter"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Iso37120Report> getReport(
            @RequestParam(required = false) Integer year) {
        String tenantId = TenantContext.getCurrentTenant();
        int effectiveYear = year != null ? year : Year.now().getValue();
        
        // Assume single city per tenant - cityId = tenantId
        String cityId = tenantId;
        
        Iso37120Report report = indicatorEngine.generate(cityId, tenantId, effectiveYear);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/indicators")
    @Operation(summary = "List all ISO 37120 indicator definitions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Indicator list returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> listIndicators() {
        // Return indicator catalog
        Map<String, String> indicators = Map.ofEntries(
            // Energy
            Map.entry("E1", "Total residential electrical energy use per capita (kWh/capita)"),
            Map.entry("E2", "Percentage of city population with authorized electrical service (%)"),
            // Environment
            Map.entry("Env1", "Fine particulate matter (PM2.5) concentration (µg/m³)"),
            Map.entry("Env2", "Greenhouse gas emissions per capita (tCO2e/capita)"),
            Map.entry("Env3", "Green area per capita (m²/capita)"),
            // Transport
            Map.entry("T1", "Kilometers of public transport per 100,000 population"),
            // Waste
            Map.entry("W1", "Total municipal solid waste per capita (tons/capita)"),
            // Governance
            Map.entry("G1", "Voter participation in last municipal election (%)"),
            Map.entry("G2", "Women as percentage of total elected to city-level office (%)")
        );
        return ResponseEntity.ok(indicators);
    }
}
