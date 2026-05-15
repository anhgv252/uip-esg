package com.uip.backend.building.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Thin proxy that forwards /api/v1/analytics/* requests to the analytics-service.
 * Keeps the frontend pointing to a single host (main backend port 8080).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Slf4j
public class AnalyticsProxyController {

    private final RestTemplate restTemplate;
    private final String analyticsServiceBaseUrl;

    public AnalyticsProxyController(
            @Value("${uip.analytics-service.url:http://analytics-service:8081/api/v1/analytics}")
            String analyticsServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.analyticsServiceBaseUrl = analyticsServiceUrl;
    }

    @PostMapping("/energy-aggregate")
    public ResponseEntity<Object> energyAggregate(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        return forward("/energy-aggregate", auth, body);
    }

    @PostMapping("/emissions-aggregate")
    public ResponseEntity<Object> emissionsAggregate(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        return forward("/emissions-aggregate", auth, body);
    }

    @PostMapping("/aqi-trend")
    public ResponseEntity<Object> aqiTrend(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        return forward("/aqi-trend", auth, body);
    }

    private ResponseEntity<Object> forward(String path, String auth, Map<String, Object> body) {
        String url = analyticsServiceBaseUrl + path;
        log.debug("[AnalyticsProxy] forwarding POST {} to {}", path, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (auth != null) {
            headers.set(HttpHeaders.AUTHORIZATION, auth);
        }

        try {
            ResponseEntity<Object> upstream = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Object.class);
            return ResponseEntity.status(upstream.getStatusCode()).body(upstream.getBody());
        } catch (HttpClientErrorException ex) {
            log.warn("[AnalyticsProxy] upstream error {}: {}", path, ex.getStatusCode());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAs(Object.class));
        } catch (Exception ex) {
            log.error("[AnalyticsProxy] analytics-service unreachable for {}: {}", path, ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "analytics-service unavailable", "path", path));
        }
    }
}
