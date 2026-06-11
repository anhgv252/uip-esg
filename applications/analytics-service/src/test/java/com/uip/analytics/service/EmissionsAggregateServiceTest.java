package com.uip.analytics.service;

import com.uip.analytics.api.dto.EmissionsAggregateRequest;
import com.uip.analytics.api.dto.EmissionsAggregateResponse;
import com.uip.analytics.api.dto.EmissionsAggregateResponse.TenantEmissionsBreakdown;
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
@DisplayName("EmissionsAggregateService — unit tests")
class EmissionsAggregateServiceTest {

    @Mock
    private ClickHouseEnergyRepository repository;

    @InjectMocks
    private EmissionsAggregateService service;

    private static final String TENANT_A = "tenant-a";
    private static final long FROM = 1000L;
    private static final long TO = 2000L;

    @Nested
    @DisplayName("aggregate()")
    class Aggregate {

        @Test
        @DisplayName("returns correct CO2 totals across buildings")
        void multipleBuildings_sumsCo2() {
            var buildings = List.of(
                    new TenantEmissionsBreakdown("B1", 500.0, 20.5),
                    new TenantEmissionsBreakdown("B2", 300.0, 12.5)
            );
            when(repository.aggregateEmissionsByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(buildings);

            var req = new EmissionsAggregateRequest(TENANT_A, List.of("B1", "B2"), FROM, TO);
            EmissionsAggregateResponse resp = service.aggregate(req);

            assertThat(resp.totalCo2Kg()).isCloseTo(800.0, within(0.001));
            assertThat(resp.tenantId()).isEqualTo(TENANT_A);
            assertThat(resp.fromEpoch()).isEqualTo(FROM);
            assertThat(resp.toEpoch()).isEqualTo(TO);
            assertThat(resp.buildings()).hasSize(2);
        }

        @Test
        @DisplayName("returns zero CO2 when no data found")
        void emptyData_returnsZeroCo2() {
            when(repository.aggregateEmissionsByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(Collections.emptyList());

            var req = new EmissionsAggregateRequest(TENANT_A, List.of(), FROM, TO);
            EmissionsAggregateResponse resp = service.aggregate(req);

            assertThat(resp.totalCo2Kg()).isCloseTo(0.0, within(0.001));
            assertThat(resp.buildings()).isEmpty();
        }

        @Test
        @DisplayName("handles null buildingIds as empty list")
        void nullBuildingIds_treatedAsEmpty() {
            when(repository.aggregateEmissionsByBuilding(eq(TENANT_A), eq(List.of()), eq(FROM), eq(TO)))
                    .thenReturn(Collections.emptyList());

            var req = new EmissionsAggregateRequest(TENANT_A, null, FROM, TO);
            service.aggregate(req);

            verify(repository).aggregateEmissionsByBuilding(TENANT_A, List.of(), FROM, TO);
        }

        @Test
        @DisplayName("single building aggregation returns correct breakdown")
        void singleBuilding_correctBreakdown() {
            var buildings = List.of(
                    new TenantEmissionsBreakdown("B1", 125.75, 5.25)
            );
            when(repository.aggregateEmissionsByBuilding(TENANT_A, List.of("B1"), FROM, TO))
                    .thenReturn(buildings);

            var req = new EmissionsAggregateRequest(TENANT_A, List.of("B1"), FROM, TO);
            EmissionsAggregateResponse resp = service.aggregate(req);

            assertThat(resp.totalCo2Kg()).isCloseTo(125.75, within(0.001));
            assertThat(resp.buildings()).hasSize(1);
            assertThat(resp.buildings().get(0).buildingId()).isEqualTo("B1");
            assertThat(resp.buildings().get(0).avgCo2PerHour()).isCloseTo(5.25, within(0.001));
        }

        @Test
        @DisplayName("building_ids filter passed through to repository")
        void buildingIdsFilter_passedToRepo() {
            when(repository.aggregateEmissionsByBuilding(TENANT_A, List.of("B1", "B3"), FROM, TO))
                    .thenReturn(Collections.emptyList());

            var req = new EmissionsAggregateRequest(TENANT_A, List.of("B1", "B3"), FROM, TO);
            service.aggregate(req);

            verify(repository).aggregateEmissionsByBuilding(TENANT_A, List.of("B1", "B3"), FROM, TO);
        }

        @Test
        @DisplayName("tenant isolation — only queried tenant data is returned")
        void tenantIsolation() {
            var buildings = List.of(
                    new TenantEmissionsBreakdown("B1", 100.0, 4.0)
            );
            when(repository.aggregateEmissionsByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(buildings);

            var req = new EmissionsAggregateRequest(TENANT_A, List.of(), FROM, TO);
            EmissionsAggregateResponse resp = service.aggregate(req);

            verify(repository).aggregateEmissionsByBuilding(eq(TENANT_A), anyList(), eq(FROM), eq(TO));
            assertThat(resp.buildings()).allSatisfy(b ->
                    assertThat(b.buildingId()).isEqualTo("B1")
            );
        }
    }
}
