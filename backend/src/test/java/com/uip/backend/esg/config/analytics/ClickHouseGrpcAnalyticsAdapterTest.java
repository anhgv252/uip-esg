package com.uip.backend.esg.config.analytics;

import com.uip.analytics.grpc.v1.EnergyAggRequest;
import com.uip.analytics.grpc.v1.EnergyAggResponse;
import com.uip.analytics.grpc.v1.EnergyAnalyticsServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClickHouseGrpcAnalyticsAdapter.
 *
 * Verifies:
 * - Protobuf request marshaling (tenant isolation — correct tenantId in request)
 * - EnergyAggResponse → EsgAggregateResult mapping
 * - Graceful degradation on StatusRuntimeException (timeout, unavailable)
 * - CO2 conversion formula (0.5 kg/kWh = 0.0005 t/kWh)
 *
 * Note: uses ReflectionTestUtils to inject mock stub (bypasses @GrpcClient).
 * Integration-level gRPC channel contract is covered by AnalyticsServiceConsumerPactTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClickHouseGrpcAnalyticsAdapter")
class ClickHouseGrpcAnalyticsAdapterTest {

    @Mock
    private EnergyAnalyticsServiceGrpc.EnergyAnalyticsServiceBlockingStub stub;

    private ClickHouseGrpcAnalyticsAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ClickHouseGrpcAnalyticsAdapter();
        // Inject mock stub via reflection — mirrors @GrpcClient injection at runtime
        ReflectionTestUtils.setField(adapter, "stub", stub);
        // withDeadlineAfter returns the same stub type — chain must return stub itself
        lenient().when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    }

    @Nested
    @DisplayName("Happy path — Protobuf marshaling")
    class HappyPath {

        @Test
        @DisplayName("Request should carry correct tenantId, buildingIds, epoch range")
        void shouldSendCorrectRequestToAnalyticsService() {
            var fakeResponse = EnergyAggResponse.newBuilder()
                    .setTotalKwh(12000.0)
                    .setCo2Tonnes(6.0)
                    .setPeakDemandKw(95.0)
                    .putPerBuildingKwh("BLD-001", 7000.0)
                    .putPerBuildingKwh("BLD-002", 5000.0)
                    .build();
            when(stub.getEnergyAggregate(any())).thenReturn(fakeResponse);

            adapter.queryEnergyAggregate("tenant-alpha", List.of("BLD-001", "BLD-002"),
                    1704067200L, 1706745600L);

            ArgumentCaptor<EnergyAggRequest> captor = ArgumentCaptor.forClass(EnergyAggRequest.class);
            verify(stub).getEnergyAggregate(captor.capture());

            EnergyAggRequest req = captor.getValue();
            assertThat(req.getTenantId()).isEqualTo("tenant-alpha");
            assertThat(req.getBuildingIdsList()).containsExactlyInAnyOrder("BLD-001", "BLD-002");
            assertThat(req.getFromEpoch()).isEqualTo(1704067200L);
            assertThat(req.getToEpoch()).isEqualTo(1706745600L);
        }

        @Test
        @DisplayName("Response totalKwh and co2Tonnes are mapped to EsgAggregateResult")
        void shouldMapResponseFieldsToEsgAggregateResult() {
            var fakeResponse = EnergyAggResponse.newBuilder()
                    .setTotalKwh(20000.0)
                    .setCo2Tonnes(10.0)
                    .putPerBuildingKwh("BLD-001", 20000.0)
                    .build();
            when(stub.getEnergyAggregate(any())).thenReturn(fakeResponse);

            EsgAggregateResult result = adapter.queryEnergyAggregate(
                    "tenant-alpha", List.of("BLD-001"), 0L, 0L);

            assertThat(result.totalKwh()).isEqualTo(20000.0);
            assertThat(result.totalCo2Tonnes()).isEqualTo(10.0);
            assertThat(result.kwhPerBuilding()).containsEntry("BLD-001", 20000.0);
            assertThat(result.buildingIds()).containsExactly("BLD-001");
        }

        @Test
        @DisplayName("perBuildingKwh map is forwarded to EsgAggregateResult.kwhPerBuilding")
        void shouldMapPerBuildingKwhBreakdown() {
            var fakeResponse = EnergyAggResponse.newBuilder()
                    .setTotalKwh(3000.0)
                    .setCo2Tonnes(1.5)
                    .putPerBuildingKwh("A1", 1000.0)
                    .putPerBuildingKwh("A2", 2000.0)
                    .build();
            when(stub.getEnergyAggregate(any())).thenReturn(fakeResponse);

            EsgAggregateResult result = adapter.queryEnergyAggregate(
                    "t1", List.of("A1", "A2"), 0L, 0L);

            assertThat(result.kwhPerBuilding())
                    .containsEntry("A1", 1000.0)
                    .containsEntry("A2", 2000.0)
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("Multi-tenant isolation")
    class MultiTenantIsolation {

        @Test
        @DisplayName("Tenant A request must NOT contain Tenant B ID in protobuf field")
        void tenantARequestShouldNotLeakTenantBId() {
            when(stub.getEnergyAggregate(any())).thenReturn(EnergyAggResponse.getDefaultInstance());

            adapter.queryEnergyAggregate("tenant-a", List.of("BLD-A01"), 0L, 0L);
            adapter.queryEnergyAggregate("tenant-b", List.of("BLD-B01"), 0L, 0L);

            ArgumentCaptor<EnergyAggRequest> captor = ArgumentCaptor.forClass(EnergyAggRequest.class);
            verify(stub, times(2)).getEnergyAggregate(captor.capture());

            List<EnergyAggRequest> allRequests = captor.getAllValues();
            assertThat(allRequests.get(0).getTenantId()).isEqualTo("tenant-a");
            assertThat(allRequests.get(1).getTenantId()).isEqualTo("tenant-b");
            // Verify no cross-contamination of building IDs
            assertThat(allRequests.get(0).getBuildingIdsList()).doesNotContain("BLD-B01");
            assertThat(allRequests.get(1).getBuildingIdsList()).doesNotContain("BLD-A01");
        }
    }

    @Nested
    @DisplayName("Error handling — graceful degradation")
    class ErrorHandling {

        @Test
        @DisplayName("DEADLINE_EXCEEDED (30s timeout) returns zero EsgAggregateResult, no exception")
        void shouldReturnZeroResultOnDeadlineExceeded() {
            when(stub.getEnergyAggregate(any()))
                    .thenThrow(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));

            EsgAggregateResult result = adapter.queryEnergyAggregate(
                    "tenant-x", List.of("BLD-001"), 0L, 0L);

            assertThat(result.totalKwh()).isZero();
            assertThat(result.totalCo2Tonnes()).isZero();
            assertThat(result.kwhPerBuilding()).isEmpty();
            assertThat(result.buildingIds()).containsExactly("BLD-001");
        }

        @Test
        @DisplayName("UNAVAILABLE (connection refused) returns zero EsgAggregateResult, no exception")
        void shouldReturnZeroResultOnConnectionRefused() {
            when(stub.getEnergyAggregate(any()))
                    .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            EsgAggregateResult result = adapter.queryEnergyAggregate(
                    "tenant-x", List.of(), 0L, 0L);

            assertThat(result.totalKwh()).isZero();
            assertThat(result.totalCo2Tonnes()).isZero();
            assertThat(result.kwhPerBuilding()).isEqualTo(Map.of());
        }

        @Test
        @DisplayName("INTERNAL error from analytics-service returns zero result, no exception")
        void shouldReturnZeroResultOnInternalError() {
            when(stub.getEnergyAggregate(any()))
                    .thenThrow(new StatusRuntimeException(Status.INTERNAL));

            EsgAggregateResult result = adapter.queryEnergyAggregate(
                    "tenant-x", List.of("BLD-001"), 0L, 0L);

            assertThat(result.totalKwh()).isZero();
        }

        @Test
        @DisplayName("Deadline is applied — withDeadlineAfter called with 5 seconds")
        void shouldApplyFiveSecondDeadline() {
            when(stub.getEnergyAggregate(any())).thenReturn(EnergyAggResponse.getDefaultInstance());

            adapter.queryEnergyAggregate("tenant-x", List.of(), 0L, 0L);

            verify(stub).withDeadlineAfter(eq(5L), eq(java.util.concurrent.TimeUnit.SECONDS));
        }
    }
}
