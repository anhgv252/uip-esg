package com.uip.backend.building.service;

import com.uip.backend.building.api.dto.BuildingCreateRequest;
import com.uip.backend.building.domain.BuildingCluster;
import com.uip.backend.building.repository.BuildingClusterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuildingClusterServiceTest {

    @Mock
    private BuildingClusterRepository repository;

    @InjectMocks
    private BuildingClusterService service;

    private BuildingCluster activeBuilding;
    private BuildingCluster inactiveBuilding;

    @BeforeEach
    void setUp() {
        activeBuilding = BuildingCluster.builder()
            .id(UUID.randomUUID())
            .buildingCode("BLD-001")
            .buildingName("Tower A")
            .tenantId("tenant-a")
            .clusterId("cluster-1")
            .floorCount(20)
            .totalAreaM2(15000.0)
            .isActive(true)
            .build();

        inactiveBuilding = BuildingCluster.builder()
            .id(UUID.randomUUID())
            .buildingCode("BLD-002")
            .buildingName("Tower B (Decommissioned)")
            .tenantId("tenant-a")
            .isActive(false)
            .build();
    }

    @Test
    void findByTenant_returnsOnlyActiveBuildings() {
        when(repository.findByTenantIdAndIsActiveTrue("tenant-a"))
            .thenReturn(List.of(activeBuilding));

        List<BuildingCluster> result = service.findByTenant("tenant-a");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBuildingCode()).isEqualTo("BLD-001");
    }

    @Test
    void findByCode_returnsBuilding_whenFoundAndActive() {
        when(repository.findByTenantIdAndBuildingCode("tenant-a", "BLD-001"))
            .thenReturn(Optional.of(activeBuilding));

        BuildingCluster result = service.findByCode("tenant-a", "BLD-001");

        assertThat(result.getBuildingCode()).isEqualTo("BLD-001");
    }

    @Test
    void findByCode_throws_whenBuildingNotFound() {
        when(repository.findByTenantIdAndBuildingCode("tenant-a", "NOT-EXIST"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("tenant-a", "NOT-EXIST"))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("NOT-EXIST");
    }

    @Test
    void findByCode_throws_whenBuildingInactive() {
        when(repository.findByTenantIdAndBuildingCode("tenant-a", "BLD-002"))
            .thenReturn(Optional.of(inactiveBuilding));

        assertThatThrownBy(() -> service.findByCode("tenant-a", "BLD-002"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void validateOwnership_throws_whenBuildingNotOwnedByTenant() {
        when(repository.findByTenantIdAndIsActiveTrue("tenant-a"))
            .thenReturn(List.of(activeBuilding));

        assertThatThrownBy(() -> service.validateOwnership("tenant-a", List.of("BLD-001", "BLD-999")))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("BLD-999");
    }

    @Test
    void validateOwnership_succeeds_whenAllBuildingsOwned() {
        when(repository.findByTenantIdAndIsActiveTrue("tenant-a"))
            .thenReturn(List.of(activeBuilding));

        assertThatNoException()
            .isThrownBy(() -> service.validateOwnership("tenant-a", List.of("BLD-001")));
    }

    @Test
    void create_throws_whenBuildingCodeAlreadyExists() {
        when(repository.existsByTenantIdAndBuildingCode("tenant-a", "BLD-001"))
            .thenReturn(true);

        BuildingCreateRequest request = new BuildingCreateRequest(
            "BLD-001", "Tower A Duplicate", null, 20, 15000.0);

        assertThatThrownBy(() -> service.create(request, "tenant-a"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BLD-001");
    }

    @Test
    void create_succeeds_withValidRequest() {
        when(repository.existsByTenantIdAndBuildingCode("tenant-a", "BLD-NEW"))
            .thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            BuildingCluster b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BuildingCreateRequest request = new BuildingCreateRequest(
            "BLD-NEW", "New Tower", "cluster-1", 30, 25000.0);

        BuildingCluster result = service.create(request, "tenant-a");

        assertThat(result.getBuildingCode()).isEqualTo("BLD-NEW");
        assertThat(result.getTenantId()).isEqualTo("tenant-a");
        assertThat(result.getIsActive()).isTrue();
        verify(repository).save(any(BuildingCluster.class));
    }

    @Test
    void create_usesDefaultFloorCount_whenFloorCountNull() {
        when(repository.existsByTenantIdAndBuildingCode(any(), any())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BuildingCreateRequest request = new BuildingCreateRequest(
            "BLD-X", "Building X", null, null, null);

        BuildingCluster result = service.create(request, "tenant-a");

        assertThat(result.getFloorCount()).isEqualTo(1);
    }
}
