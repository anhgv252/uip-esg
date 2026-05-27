package com.uip.backend.forecast;

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
public class ForecastCacheStatsController {

    private final ForecastCacheStatsService cacheStatsService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(cacheStatsService.getCacheStats());
    }
}
