package com.uip.backend.forecast;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST client adapter → Python forecast-service.
 *
 * ADR-032 D4: X-Tenant-ID header pass-through from SecurityContext.
 * ADR-032 D1: Python service is Docker-internal only, no host port.
 */
@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "uip.capabilities.forecast-engine", havingValue = "python")
public class ForecastServiceAdapter implements ForecastPort {

    private final RestClient restClient;

    public ForecastServiceAdapter(@Value("${uip.forecast.service-url}") String baseUrl,
                                  RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public ForecastResult forecast(String tenantId, String buildingId, int horizonDays) {
        log.info("Calling forecast-service: buildingId={}, horizonDays={}, tenant={}",
                buildingId, horizonDays, tenantId);

        try {
            String traceId = MDC.get("traceId");

            Map<String, Object> response = restClient.get()
                    .uri("/api/v1/forecast/energy?building_id={bid}&horizon_days={hd}",
                            buildingId, horizonDays)
                    .header("X-Tenant-ID", tenantId)
                    .header("X-Trace-Id", traceId != null ? traceId : "")
                    .retrieve()
                    .body(Map.class);

            return mapResponse(response, tenantId, buildingId);
        } catch (Exception e) {
            log.error("forecast-service call failed: {}", e.getMessage());
            throw new ForecastServiceUnavailableException("Forecast service unavailable", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ForecastResult mapResponse(Map<String, Object> response, String tenantId, String buildingId) {
        Object rawPointsObj = response.get("points");
        if (rawPointsObj == null) {
            log.warn("Forecast response missing 'points' field, returning empty");
            return ForecastResult.insufficientData(tenantId, buildingId);
        }
        List<Map<String, Object>> rawPoints = (List<Map<String, Object>>) rawPointsObj;
        List<ForecastPoint> points = rawPoints.stream()
                .map(p -> new ForecastPoint(
                        Instant.parse((String) p.get("timestamp")),
                        (Double) p.get("actual_value"),
                        (Double) p.get("predicted_value"),
                        (Double) p.get("confidence_upper"),
                        (Double) p.get("confidence_lower"),
                        Boolean.TRUE.equals(p.get("is_anomaly"))
                ))
                .toList();

        String generatedAtStr = (String) response.get("generated_at");
        Instant generatedAt = generatedAtStr != null ? Instant.parse(generatedAtStr) : Instant.now();

        return new ForecastResult(
                (String) response.getOrDefault("tenant_id", tenantId),
                (String) response.getOrDefault("building_id", buildingId),
                (String) response.getOrDefault("model", "ARIMA"),
                Boolean.TRUE.equals(response.get("is_fallback")),
                (Double) response.get("mape"),
                points,
                generatedAt
        );
    }
}
