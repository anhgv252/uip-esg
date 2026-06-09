package com.uip.analytics.grpc;

import com.uip.analytics.api.dto.EnergyAggregateRequest;
import com.uip.analytics.api.dto.EnergyAggregateResponse;
import com.uip.analytics.grpc.v1.EnergyAggRequest;
import com.uip.analytics.grpc.v1.EnergyAggResponse;
import com.uip.analytics.grpc.v1.EnergyAnalyticsServiceGrpc;
import com.uip.analytics.service.EnergyAggregateService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * gRPC server implementation for energy analytics queries (ADR-012).
 *
 * <p>Receives requests from the backend monolith via gRPC instead of REST.
 * Reuses existing {@link EnergyAggregateService} business logic.</p>
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class EnergyAnalyticsGrpcService
        extends EnergyAnalyticsServiceGrpc.EnergyAnalyticsServiceImplBase {

    private final EnergyAggregateService aggregateService;

    @Override
    public void getEnergyAggregate(EnergyAggRequest request,
                                    StreamObserver<EnergyAggResponse> responseObserver) {
        try {
            log.debug("[gRPC] energy aggregate: tenant={} buildings={}",
                    request.getTenantId(), request.getBuildingIdsCount());

            // Map proto → domain DTO
            var domainRequest = new EnergyAggregateRequest(
                    request.getTenantId(),
                    request.getBuildingIdsList(),
                    request.getFromEpoch(),
                    request.getToEpoch()
            );

            // Call existing service
            var result = aggregateService.aggregate(domainRequest);

            // Map domain DTO → proto response
            Map<String, Double> perBuildingKwh = result.buildings() != null
                    ? result.buildings().stream()
                        .collect(Collectors.toMap(
                            EnergyAggregateResponse.BuildingEnergyBreakdown::buildingId,
                            EnergyAggregateResponse.BuildingEnergyBreakdown::totalKwh))
                    : Map.of();

            double co2 = result.totalKwh() * 0.0005; // 0.5 kg/kWh → tonnes

            var response = EnergyAggResponse.newBuilder()
                    .setTotalKwh(result.totalKwh())
                    .setPeakDemandKw(result.peakDemandKw())
                    .setAvgPowerFactor(result.averagePowerFactor())
                    .setCo2Tonnes(co2)
                    .putAllPerBuildingKwh(perBuildingKwh)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] energy aggregate failed: {}", e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Aggregate query failed: " + e.getMessage())
                    .asRuntimeException());
        }
    }
}
