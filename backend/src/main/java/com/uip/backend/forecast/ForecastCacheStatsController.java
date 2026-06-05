package com.uip.backend.forecast;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Cache stats endpoint for forecast module (S4-17).
 */
@RestController
@RequestMapping("/api/v1/forecast/cache")
@RequiredArgsConstructor
@Tag(name = "Forecast Cache", description = "Forecast cache statistics (ADMIN)")
@SecurityRequirement(name = "Bearer Authentication")
public class ForecastCacheStatsController {

    private final ForecastCacheStatsService cacheStatsService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get forecast cache statistics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cache stats returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    })
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(cacheStatsService.getCacheStats());
    }
}
