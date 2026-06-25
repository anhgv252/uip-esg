package com.uip.analytics.grpc;

import com.uip.analytics.api.dto.EnergyAggregateRequest;
import com.uip.analytics.api.dto.EnergyAggregateResponse;
import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import com.uip.analytics.grpc.v1.EnergyAggRequest;
import com.uip.analytics.grpc.v1.EnergyAggResponse;
import com.uip.analytics.grpc.v1.EnergyAnalyticsServiceGrpc;
import com.uip.analytics.grpc.v1.EnergyAnalyticsServiceGrpc.EnergyAnalyticsServiceBlockingStub;
import com.uip.analytics.service.EnergyAggregateService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test cho EnergyAnalyticsGrpcService qua gRPC wire thật
 * (InProcess channel — không bind port 9090, CI-safe).
 *
 * <p>Khác với {@link EnergyAnalyticsGrpcServiceTest} (unit mock StreamObserver),
 * IT này tạo real gRPC server + channel → xác minh proto serialization,
 * request mapping qua wire, response marshaling. {@link EnergyAggregateService}
 * được mock để focus gRPC layer, không cần ClickHouse.</p>
 *
 * <p>MVP5 Sprint M5-1 Task T11 deliverable: gRPC IT infra + sample tests.</p>
 */
@Tag("integration")
@ExtendWith(MockitoExtension.class)
@DisplayName("EnergyAnalyticsGrpcService — gRPC wire IT (InProcess channel)")
class EnergyAnalyticsGrpcServiceIT {

    @Mock
    private EnergyAggregateService aggregateService;

    private Server server;
    private ManagedChannel channel;
    private EnergyAnalyticsServiceBlockingStub blockingStub;

    private static final String TENANT = "t1";
    private static final long FROM = 1_700_000_000L;
    private static final long TO = 1_700_003_600L;

    @BeforeEach
    void setUp() throws IOException {
        var grpcService = new EnergyAnalyticsGrpcService(aggregateService);

        // Unique name tránh collision khi chạy parallel test.
        String serverName = InProcessServerBuilder.generateName();

        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(grpcService)
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        blockingStub = EnergyAnalyticsServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("valid request qua wire → response fields marshal đúng proto")
    void validRequest_roundTripsThroughGrpcWire() {
        // Arrange: stub service trả fixture (no ClickHouse needed).
        var fixture = new EnergyAggregateResponse(
                TENANT, FROM, TO, 450.0, 200.0, 0.95,
                List.of(
                        new BuildingEnergyBreakdown("B1", 300.0, 200.0),
                        new BuildingEnergyBreakdown("B2", 150.0, 150.0)
                )
        );
        when(aggregateService.aggregate(any(EnergyAggregateRequest.class)))
                .thenReturn(fixture);

        // Act: gửi request qua gRPC channel thật (proto serialize → wire → deserialize).
        var request = EnergyAggRequest.newBuilder()
                .setTenantId(TENANT)
                .addBuildingIds("B1")
                .addBuildingIds("B2")
                .setFromEpoch(FROM)
                .setToEpoch(TO)
                .build();

        EnergyAggResponse response = blockingStub.getEnergyAggregate(request);

        // Assert: response fields round-trip chính xác qua proto.
        assertThat(response.getTotalKwh()).isCloseTo(450.0, within(0.001));
        assertThat(response.getPeakDemandKw()).isCloseTo(200.0, within(0.001));
        assertThat(response.getAvgPowerFactor()).isCloseTo(0.95, within(0.001));
        // CO2 = totalKwh * 0.0005 → 450 * 0.0005 = 0.225 tonnes
        assertThat(response.getCo2Tonnes()).isCloseTo(0.225, within(0.001));
        assertThat(response.getPerBuildingKwhMap())
                .hasSize(2)
                .containsEntry("B1", 300.0)
                .containsEntry("B2", 150.0);
    }

    @Test
    @DisplayName("service exception qua wire → gRPC INTERNAL status")
    void serviceException_returnsGrpcInternalStatus() {
        when(aggregateService.aggregate(any(EnergyAggregateRequest.class)))
                .thenThrow(new RuntimeException("simulated ClickHouse failure"));

        var request = EnergyAggRequest.newBuilder()
                .setTenantId(TENANT)
                .setFromEpoch(FROM)
                .setToEpoch(TO)
                .build();

        // Blocking stub throws StatusRuntimeException khi server onError.
        var thrown = catchThrowableOfType(
                () -> blockingStub.getEnergyAggregate(request),
                StatusRuntimeException.class);

        assertThat(thrown.getStatus().getCode()).isEqualTo(Code.INTERNAL);
        assertThat(thrown.getStatus().getDescription())
                .contains("Aggregate query failed");
    }

    @Test
    @DisplayName("empty building_ids qua wire → empty per_building_kwh map")
    void emptyBuildingIds_returnsEmptyMap() {
        var fixture = new EnergyAggregateResponse(
                TENANT, FROM, TO, 100.0, 50.0, 1.0, List.of()
        );
        when(aggregateService.aggregate(any(EnergyAggregateRequest.class)))
                .thenReturn(fixture);

        var request = EnergyAggRequest.newBuilder()
                .setTenantId(TENANT)
                .setFromEpoch(FROM)
                .setToEpoch(TO)
                .build();

        EnergyAggResponse response = blockingStub.getEnergyAggregate(request);

        assertThat(response.getPerBuildingKwhMap()).isEmpty();
        assertThat(response.getTotalKwh()).isCloseTo(100.0, within(0.001));
    }
}
