package com.uip.backend.esg.config.analytics;

import com.uip.analytics.grpc.v1.EnergyAggRequest;
import com.uip.analytics.grpc.v1.EnergyAggResponse;
import com.uip.analytics.grpc.v1.EnergyAnalyticsServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tier 2+ gRPC implementation của AnalyticsPort (ADR-012).
 *
 * <p>Loads when BOTH analytics-external=true AND analytics-transport=grpc.
 * Replaces REST adapter for backend → analytics-service communication.</p>
 *
 * <p>Graceful fallback: returns zero result when gRPC call fails,
 * so ESG reporting continues (with zero data) rather than 500.</p>
 */
@Component
@ConditionalOnProperty(
    name = "uip.capabilities.analytics-transport",
    havingValue = "grpc"
)
@Slf4j
public class ClickHouseGrpcAnalyticsAdapter implements AnalyticsPort {

    @GrpcClient("analytics-service")
    private EnergyAnalyticsServiceGrpc.EnergyAnalyticsServiceBlockingStub stub;

    @Override
    public EsgAggregateResult queryEnergyAggregate(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {
        try {
            var request = EnergyAggRequest.newBuilder()
                    .setTenantId(tenantId)
                    .addAllBuildingIds(buildingIds)
                    .setFromEpoch(fromEpoch)
                    .setToEpoch(toEpoch)
                    .build();

            EnergyAggResponse response = stub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .getEnergyAggregate(request);

            return new EsgAggregateResult(
                    response.getTotalKwh(),
                    response.getCo2Tonnes(),
                    response.getPerBuildingKwhMap(),
                    buildingIds
            );
        } catch (StatusRuntimeException e) {
            log.error("[Analytics-gRPC] call failed: {} for tenant={}", e.getStatus(), tenantId);
            return new EsgAggregateResult(0.0, 0.0, Map.of(), buildingIds);
        }
    }
}
