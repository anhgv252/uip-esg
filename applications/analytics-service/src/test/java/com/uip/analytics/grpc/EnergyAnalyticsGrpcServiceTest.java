package com.uip.analytics.grpc;

import com.uip.analytics.api.dto.EnergyAggregateResponse;
import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import com.uip.analytics.grpc.v1.EnergyAggRequest;
import com.uip.analytics.grpc.v1.EnergyAggResponse;
import com.uip.analytics.grpc.v1.EnergyAnalyticsServiceGrpc;
import com.uip.analytics.service.EnergyAggregateService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnergyAnalyticsGrpcService — unit tests")
class EnergyAnalyticsGrpcServiceTest {

    @Mock
    private EnergyAggregateService aggregateService;

    @InjectMocks
    private EnergyAnalyticsGrpcService grpcService;

    @Mock
    private StreamObserver<EnergyAggResponse> responseObserver;

    private static final String TENANT_A = "tenant-a";
    private static final long FROM = 1000L;
    private static final long TO = 2000L;

    @Nested
    @DisplayName("getEnergyAggregate()")
    class GetEnergyAggregate {

        @Test
        @DisplayName("valid request returns correct response fields")
        void validRequest_mapsFieldsCorrectly() {
            var domainResponse = new EnergyAggregateResponse(
                    TENANT_A, FROM, TO, 450.0, 200.0, 0.95,
                    List.of(
                            new BuildingEnergyBreakdown("B1", 300.0, 200.0),
                            new BuildingEnergyBreakdown("B2", 150.0, 150.0)
                    )
            );
            when(aggregateService.aggregate(any())).thenReturn(domainResponse);

            var request = EnergyAggRequest.newBuilder()
                    .setTenantId(TENANT_A)
                    .addBuildingIds("B1")
                    .addBuildingIds("B2")
                    .setFromEpoch(FROM)
                    .setToEpoch(TO)
                    .build();

            grpcService.getEnergyAggregate(request, responseObserver);

            ArgumentCaptor<EnergyAggResponse> captor = ArgumentCaptor.forClass(EnergyAggResponse.class);
            verify(responseObserver).onNext(captor.capture());
            verify(responseObserver).onCompleted();

            EnergyAggResponse resp = captor.getValue();
            assertThat(resp.getTotalKwh()).isCloseTo(450.0, within(0.001));
            assertThat(resp.getPeakDemandKw()).isCloseTo(200.0, within(0.001));
            assertThat(resp.getAvgPowerFactor()).isCloseTo(0.95, within(0.001));
            // CO2 = totalKwh * 0.0005
            assertThat(resp.getCo2Tonnes()).isCloseTo(0.225, within(0.001));
            assertThat(resp.getPerBuildingKwhMap()).containsEntry("B1", 300.0);
            assertThat(resp.getPerBuildingKwhMap()).containsEntry("B2", 150.0);
        }

        @Test
        @DisplayName("empty building_ids returns aggregate with empty map")
        void emptyBuildingIds_returnsEmptyMap() {
            var domainResponse = new EnergyAggregateResponse(
                    TENANT_A, FROM, TO, 100.0, 50.0, 1.0, List.of()
            );
            when(aggregateService.aggregate(any())).thenReturn(domainResponse);

            var request = EnergyAggRequest.newBuilder()
                    .setTenantId(TENANT_A)
                    .setFromEpoch(FROM)
                    .setToEpoch(TO)
                    .build();

            grpcService.getEnergyAggregate(request, responseObserver);

            ArgumentCaptor<EnergyAggResponse> captor = ArgumentCaptor.forClass(EnergyAggResponse.class);
            verify(responseObserver).onNext(captor.capture());

            EnergyAggResponse resp = captor.getValue();
            assertThat(resp.getPerBuildingKwhMap()).isEmpty();
            assertThat(resp.getTotalKwh()).isCloseTo(100.0, within(0.001));
        }

        @Test
        @DisplayName("null buildings list handled gracefully")
        void nullBuildingsList_returnsEmptyMap() {
            var domainResponse = new EnergyAggregateResponse(
                    TENANT_A, FROM, TO, 0.0, 0.0, 1.0, null
            );
            when(aggregateService.aggregate(any())).thenReturn(domainResponse);

            var request = EnergyAggRequest.newBuilder()
                    .setTenantId(TENANT_A)
                    .setFromEpoch(FROM)
                    .setToEpoch(TO)
                    .build();

            grpcService.getEnergyAggregate(request, responseObserver);

            ArgumentCaptor<EnergyAggResponse> captor = ArgumentCaptor.forClass(EnergyAggResponse.class);
            verify(responseObserver).onNext(captor.capture());

            assertThat(captor.getValue().getPerBuildingKwhMap()).isEmpty();
        }

        @Test
        @DisplayName("CO2 calculation uses 0.5 kg/kWh conversion factor")
        void co2Calculation_correctConversion() {
            var domainResponse = new EnergyAggregateResponse(
                    TENANT_A, FROM, TO, 1000.0, 500.0, 0.9, List.of()
            );
            when(aggregateService.aggregate(any())).thenReturn(domainResponse);

            var request = EnergyAggRequest.newBuilder()
                    .setTenantId(TENANT_A)
                    .setFromEpoch(FROM)
                    .setToEpoch(TO)
                    .build();

            grpcService.getEnergyAggregate(request, responseObserver);

            ArgumentCaptor<EnergyAggResponse> captor = ArgumentCaptor.forClass(EnergyAggResponse.class);
            verify(responseObserver).onNext(captor.capture());
            // 1000.0 * 0.0005 = 0.5 tonnes
            assertThat(captor.getValue().getCo2Tonnes()).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("repository exception returns INTERNAL gRPC status")
        void repositoryException_returnsInternalError() {
            when(aggregateService.aggregate(any()))
                    .thenThrow(new RuntimeException("ClickHouse connection refused"));

            var request = EnergyAggRequest.newBuilder()
                    .setTenantId(TENANT_A)
                    .setFromEpoch(FROM)
                    .setToEpoch(TO)
                    .build();

            grpcService.getEnergyAggregate(request, responseObserver);

            verify(responseObserver).onError(any());
            verify(responseObserver, never()).onNext(any());
            verify(responseObserver, never()).onCompleted();
        }

        @Test
        @DisplayName("request field mapping — tenant_id, building_ids, epochs")
        void requestMapping_correctDomainDto() {
            when(aggregateService.aggregate(any())).thenReturn(
                    new EnergyAggregateResponse(TENANT_A, FROM, TO, 0.0, 0.0, 1.0, List.of())
            );

            var request = EnergyAggRequest.newBuilder()
                    .setTenantId(TENANT_A)
                    .addBuildingIds("B1")
                    .addBuildingIds("B2")
                    .addBuildingIds("B3")
                    .setFromEpoch(FROM)
                    .setToEpoch(TO)
                    .build();

            grpcService.getEnergyAggregate(request, responseObserver);

            ArgumentCaptor<com.uip.analytics.api.dto.EnergyAggregateRequest> domainCaptor =
                    ArgumentCaptor.forClass(com.uip.analytics.api.dto.EnergyAggregateRequest.class);
            verify(aggregateService).aggregate(domainCaptor.capture());

            var captured = domainCaptor.getValue();
            assertThat(captured.tenantId()).isEqualTo(TENANT_A);
            assertThat(captured.buildingIds()).containsExactly("B1", "B2", "B3");
            assertThat(captured.fromEpoch()).isEqualTo(FROM);
            assertThat(captured.toEpoch()).isEqualTo(TO);
        }
    }
}
