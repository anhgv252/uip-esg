package com.uip.backend.building.service;

import com.uip.backend.building.api.dto.CrossBuildingAggregationRequest;
import com.uip.backend.building.api.dto.CrossBuildingAggregationResult;
import com.uip.backend.building.domain.BuildingCluster;
import com.uip.backend.building.repository.BuildingClusterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrossBuildingAggregationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private BuildingClusterRepository buildingRepository;

    @InjectMocks
    private CrossBuildingAggregationService service;

    private BuildingCluster buildingA1;
    private BuildingCluster buildingA2;

    @BeforeEach
    void setUp() {
        buildingA1 = BuildingCluster.builder()
            .id(UUID.randomUUID())
            .buildingCode("BLD-001")
            .buildingName("Tower A1")
            .tenantId("tenant-a")
            .clusterId("cluster-a")
            .isActive(true)
            .build();

        buildingA2 = BuildingCluster.builder()
            .id(UUID.randomUUID())
            .buildingCode("BLD-002")
            .buildingName("Tower A2")
            .tenantId("tenant-a")
            .clusterId("cluster-a")
            .isActive(true)
            .build();
    }

    @Test
    void aggregate_returnsEmptyList_whenNoBuildingsFoundForTenant() {
        when(buildingRepository.findByTenantIdAndIsActiveTrue("tenant-a"))
            .thenReturn(List.of());

        CrossBuildingAggregationRequest request = new CrossBuildingAggregationRequest(
            List.of("BLD-001", "BLD-002"),
            "ENERGY",
            OffsetDateTime.now().minusDays(7),
            OffsetDateTime.now()
        );

        List<CrossBuildingAggregationResult> result = service.aggregate("tenant-a", request);

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void aggregate_returnsEmptyList_whenRequestedBuildingsNotInTenantBuildings() {
        when(buildingRepository.findByTenantIdAndIsActiveTrue("tenant-a"))
            .thenReturn(List.of(buildingA1));

        CrossBuildingAggregationRequest request = new CrossBuildingAggregationRequest(
            List.of("BLD-999"),
            "ENERGY",
            OffsetDateTime.now().minusDays(7),
            OffsetDateTime.now()
        );

        List<CrossBuildingAggregationResult> result = service.aggregate("tenant-a", request);

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void aggregate_executesQueryAndReturnsResults_whenBuildingsFound() throws Exception {
        when(buildingRepository.findByTenantIdAndIsActiveTrue("tenant-a"))
            .thenReturn(List.of(buildingA1, buildingA2));

        CrossBuildingAggregationRequest request = new CrossBuildingAggregationRequest(
            List.of("BLD-001", "BLD-002"),
            "ENERGY",
            OffsetDateTime.now().minusDays(7),
            OffsetDateTime.now()
        );

        CrossBuildingAggregationResult expected = new CrossBuildingAggregationResult(
            "BLD-001", "Tower A1", 1500.0, 750.0, 200L, "kWh"
        );

        // Mock JdbcTemplate.execute(ConnectionCallback) → return result list
        when(jdbcTemplate.execute(any(org.springframework.jdbc.core.ConnectionCallback.class)))
            .thenAnswer(inv -> List.of(expected));

        List<CrossBuildingAggregationResult> result = service.aggregate("tenant-a", request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).buildingCode()).isEqualTo("BLD-001");
        assertThat(result.get(0).totalValue()).isEqualTo(1500.0);
    }

    @Test
    void aggregate_filtersOutBuildingsNotInRequest() {
        when(buildingRepository.findByTenantIdAndIsActiveTrue("tenant-a"))
            .thenReturn(List.of(buildingA1, buildingA2));

        // Request only BLD-001 — BLD-002 is a valid tenant building but NOT requested
        CrossBuildingAggregationRequest request = new CrossBuildingAggregationRequest(
            List.of("BLD-001"),
            "WATER",
            OffsetDateTime.now().minusDays(30),
            OffsetDateTime.now()
        );

        when(jdbcTemplate.execute(any(org.springframework.jdbc.core.ConnectionCallback.class)))
            .thenAnswer(inv -> List.of());

        List<CrossBuildingAggregationResult> result = service.aggregate("tenant-a", request);

        // jdbcTemplate was called (at least one building in map), with only BLD-001
        verify(jdbcTemplate).execute(any(org.springframework.jdbc.core.ConnectionCallback.class));
        assertThat(result).isEmpty();
    }

    @Test
    void aggregate_returnsEmptyList_whenRepositoryReturnsInactiveBuildingsOnly() {
        // Repository's findByTenantIdAndIsActiveTrue filters inactive — simulating correct behavior
        when(buildingRepository.findByTenantIdAndIsActiveTrue("tenant-a"))
            .thenReturn(List.of());

        CrossBuildingAggregationRequest request = new CrossBuildingAggregationRequest(
            List.of("BLD-INACTIVE"),
            "ENERGY",
            OffsetDateTime.now().minusDays(7),
            OffsetDateTime.now()
        );

        List<CrossBuildingAggregationResult> result = service.aggregate("tenant-a", request);

        assertThat(result).isEmpty();
    }
}
