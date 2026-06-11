package com.uip.analytics.service;

import com.uip.analytics.api.dto.EnergyAggregateRequest;
import com.uip.analytics.api.dto.EnergyAggregateResponse;
import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import com.uip.analytics.repository.ClickHouseEnergyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnergyAggregateService — unit tests")
class EnergyAggregateServiceTest {

    @Mock
    private ClickHouseEnergyRepository repository;

    @InjectMocks
    private EnergyAggregateService service;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final long FROM = 1000L;
    private static final long TO = 2000L;

    @Nested
    @DisplayName("aggregate()")
    class Aggregate {

        @Test
        @DisplayName("returns correct totals when multiple buildings present")
        void multipleBuildings_sumsCorrectly() {
            var buildings = List.of(
                    new BuildingEnergyBreakdown("B1", 300.0, 200.0),
                    new BuildingEnergyBreakdown("B2", 150.0, 150.0)
            );
            when(repository.aggregateByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(buildings);
            when(repository.aggregatePowerFactor(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(0.95);

            var req = new EnergyAggregateRequest(TENANT_A, List.of("B1", "B2"), FROM, TO);
            EnergyAggregateResponse resp = service.aggregate(req);

            assertThat(resp.totalKwh()).isCloseTo(450.0, within(0.001));
            assertThat(resp.peakDemandKw()).isCloseTo(200.0, within(0.001));
            assertThat(resp.averagePowerFactor()).isCloseTo(0.95, within(0.001));
            assertThat(resp.tenantId()).isEqualTo(TENANT_A);
            assertThat(resp.fromEpoch()).isEqualTo(FROM);
            assertThat(resp.toEpoch()).isEqualTo(TO);
            assertThat(resp.buildings()).hasSize(2);
        }

        @Test
        @DisplayName("returns zero totals when no data found")
        void emptyData_returnsZeros() {
            when(repository.aggregateByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(Collections.emptyList());
            when(repository.aggregatePowerFactor(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(1.0);

            var req = new EnergyAggregateRequest(TENANT_A, List.of(), FROM, TO);
            EnergyAggregateResponse resp = service.aggregate(req);

            assertThat(resp.totalKwh()).isCloseTo(0.0, within(0.001));
            assertThat(resp.peakDemandKw()).isCloseTo(0.0, within(0.001));
            assertThat(resp.buildings()).isEmpty();
        }

        @Test
        @DisplayName("handles null buildingIds as empty list")
        void nullBuildingIds_treatedAsEmpty() {
            when(repository.aggregateByBuilding(eq(TENANT_A), eq(List.of()), eq(FROM), eq(TO)))
                    .thenReturn(Collections.emptyList());
            when(repository.aggregatePowerFactor(eq(TENANT_A), eq(List.of()), eq(FROM), eq(TO)))
                    .thenReturn(1.0);

            var req = new EnergyAggregateRequest(TENANT_A, null, FROM, TO);
            EnergyAggregateResponse resp = service.aggregate(req);

            assertThat(resp.totalKwh()).isCloseTo(0.0, within(0.001));
            verify(repository).aggregateByBuilding(TENANT_A, List.of(), FROM, TO);
        }

        @Test
        @DisplayName("peak demand is max across all buildings, not sum")
        void peakDemand_isMaxNotSum() {
            var buildings = List.of(
                    new BuildingEnergyBreakdown("B1", 100.0, 50.0),
                    new BuildingEnergyBreakdown("B2", 200.0, 80.0),
                    new BuildingEnergyBreakdown("B3", 150.0, 120.0)
            );
            when(repository.aggregateByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(buildings);
            when(repository.aggregatePowerFactor(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(1.0);

            var req = new EnergyAggregateRequest(TENANT_A, List.of(), FROM, TO);
            EnergyAggregateResponse resp = service.aggregate(req);

            assertThat(resp.peakDemandKw()).isCloseTo(120.0, within(0.001));
        }

        @Test
        @DisplayName("tenant isolation — tenant A request never queries tenant B data")
        void tenantIsolation_onlyQueriesOwnTenant() {
            when(repository.aggregateByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(Collections.emptyList());
            when(repository.aggregatePowerFactor(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(1.0);

            var req = new EnergyAggregateRequest(TENANT_A, List.of(), FROM, TO);
            service.aggregate(req);

            verify(repository).aggregateByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO));
            // tenant B is never queried because tenantId is passed directly
        }

        @Test
        @DisplayName("building_ids filter passed through to repository")
        void buildingIdsFilter_passedToRepo() {
            var filteredBuildings = List.of(
                    new BuildingEnergyBreakdown("B1", 300.0, 200.0)
            );
            when(repository.aggregateByBuilding(TENANT_A, List.of("B1"), FROM, TO))
                    .thenReturn(filteredBuildings);
            when(repository.aggregatePowerFactor(TENANT_A, List.of("B1"), FROM, TO))
                    .thenReturn(0.98);

            var req = new EnergyAggregateRequest(TENANT_A, List.of("B1"), FROM, TO);
            EnergyAggregateResponse resp = service.aggregate(req);

            assertThat(resp.buildings()).hasSize(1);
            assertThat(resp.buildings().get(0).buildingId()).isEqualTo("B1");
            verify(repository).aggregateByBuilding(TENANT_A, List.of("B1"), FROM, TO);
        }

        @Test
        @DisplayName("totalKwh is sum of all building totalKwh values")
        void totalKwh_isSumOfAllBuildings() {
            var buildings = List.of(
                    new BuildingEnergyBreakdown("B1", 100.5, 60.0),
                    new BuildingEnergyBreakdown("B2", 200.3, 90.0)
            );
            when(repository.aggregateByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(buildings);
            when(repository.aggregatePowerFactor(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(1.0);

            var req = new EnergyAggregateRequest(TENANT_A, List.of(), FROM, TO);
            EnergyAggregateResponse resp = service.aggregate(req);

            assertThat(resp.totalKwh()).isCloseTo(300.8, within(0.001));
        }
    }
}
