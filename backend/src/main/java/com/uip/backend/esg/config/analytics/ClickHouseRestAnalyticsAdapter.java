package com.uip.backend.esg.config.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tier 2+ REST implementation của AnalyticsPort.
 * Delegate sang analytics-service (ClickHouse owner) qua HTTP REST.
 *
 * <p>Loads when analytics-external=true.
 * When analytics-transport=grpc, {@link ClickHouseGrpcAnalyticsAdapter} loads instead.
 * IMPORTANT: Deployment config must ensure only ONE adapter is active (not both).</p>
 */
@Component
@ConditionalOnExpression("${uip.capabilities.analytics-external:false} && '${uip.capabilities.analytics-transport:rest}'.equals('rest')")
@Slf4j
public class ClickHouseRestAnalyticsAdapter implements AnalyticsPort {

    private final RestTemplate restTemplate;
    private final String analyticsServiceUrl;

    public ClickHouseRestAnalyticsAdapter(
            @Value("${uip.analytics-service.url:http://localhost:8082}") String analyticsServiceUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
        this.analyticsServiceUrl = analyticsServiceUrl;
    }

    @Override
    public EsgAggregateResult queryEnergyAggregate(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {

        log.debug("[Analytics-T2] analytics-service call: tenant={} from={} to={}", tenantId, fromEpoch, toEpoch);

        EnergyAggregateHttpResponse response;
        try {
            var request = new EnergyAggregateHttpRequest(tenantId, buildingIds, fromEpoch, toEpoch);
            response = restTemplate.postForObject(
                    analyticsServiceUrl + "/energy-aggregate",
                    request,
                    EnergyAggregateHttpResponse.class);
        } catch (RestClientException e) {
            // analytics-service down hoặc timeout → trả về zero thay vì 500 lên client
            log.error("[Analytics-T2] analytics-service unavailable for tenant={}: {}", tenantId, e.getMessage());
            return new EsgAggregateResult(0.0, 0.0, Map.of(), buildingIds);
        }

        if (response == null) {
            log.warn("[Analytics-T2] analytics-service returned null for tenant={}", tenantId);
            return new EsgAggregateResult(0.0, 0.0, Map.of(), buildingIds);
        }

        Map<String, Double> kwhPerBuilding = response.buildings() == null ? Map.of()
                : response.buildings().stream()
                    .collect(Collectors.toMap(
                        EnergyAggregateHttpResponse.BuildingBreakdown::buildingId,
                        EnergyAggregateHttpResponse.BuildingBreakdown::totalKwh));

        // CO2 estimate: 0.5 kg/kWh → 0.0005 tonnes/kWh
        double co2 = response.totalKwh() * 0.0005;

        return new EsgAggregateResult(response.totalKwh(), co2, kwhPerBuilding, buildingIds);
    }

    // ── HTTP request/response DTOs (internal — không share với analytics-service code) ──

    record EnergyAggregateHttpRequest(
        String tenantId,
        List<String> buildingIds,
        long fromEpoch,
        long toEpoch
    ) {}

    record EnergyAggregateHttpResponse(
        String tenantId,
        long fromEpoch,
        long toEpoch,
        double totalKwh,
        double peakDemandKw,
        double averagePowerFactor,
        List<BuildingBreakdown> buildings
    ) {
        record BuildingBreakdown(String buildingId, double totalKwh, double peakDemandKw) {}
    }
}
