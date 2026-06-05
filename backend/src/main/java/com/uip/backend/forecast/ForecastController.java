package com.uip.backend.forecast;

import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Forecast REST endpoint.
 *
 * ADR-032 D4: tenantId from JWT (TenantContext), NOT query param.
 * Response time thresholds per ADR-032 AC revision.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/forecast")
@RequiredArgsConstructor
@Tag(name = "Forecast", description = "Energy forecast API")
@SecurityRequirement(name = "Bearer Authentication")
public class ForecastController {

    private final ForecastService forecastService;

    @GetMapping("/energy")
    @Operation(summary = "Get energy forecast for a building")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Forecast result returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Building not found"),
        @ApiResponse(responseCode = "503", description = "Forecast service unavailable")
    })
    public ResponseEntity<ForecastResult> forecastEnergy(
            @RequestParam String buildingId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int horizonDays
    ) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(403).build();
        }

        log.info("Forecast request: tenant={}, building={}, horizon={}d", tenantId, buildingId, horizonDays);

        try {
            ForecastResult result = forecastService.forecast(tenantId, buildingId, horizonDays);
            return ResponseEntity.ok(result);
        } catch (ForecastServiceUnavailableException e) {
            log.warn("Forecast service unavailable, returning 503: {}", e.getMessage());
            return ResponseEntity.status(503).build();
        }
    }
}
