package com.uip.backend.esg.config.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tier 2+ implementation của AnalyticsPort.
 * Delegate sang analytics-service (ClickHouse owner) qua HTTP REST.
 *
 * Được load KHI analytics-external=true (Tier 2, sau cutover Sprint 2).
 * AnalyticsAutoConfiguration sẽ KHÔNG load TimescaleDbAnalyticsAdapter.
 *
 * Business code (EsgService) không đổi — chỉ biết AnalyticsPort interface.
 */
@Component
@ConditionalOnProperty(
    name        = "uip.capabilities.analytics-external",
    havingValue = "true"  // chỉ load khi flag = true
)
@Slf4j
public class ClickHouseRestAnalyticsAdapter implements AnalyticsPort {

    // In production: @Value("${uip.analytics-service.url}") + inject RestTemplate/WebClient

    @Override
    public EsgAggregateResult queryEnergyAggregate(
            String tenantId,
            List<String> buildingIds,
            long fromEpoch,
            long toEpoch) {
        log.debug("[Analytics] ClickHouse REST query via analytics-service: tenant={}", tenantId);
        // In production: restTemplate.postForObject(url, request, EsgAggregateResult.class)
        return new EsgAggregateResult(0.0, 0.0, Map.of(), buildingIds);
    }
}
