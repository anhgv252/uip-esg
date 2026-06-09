package com.uip.backend.building;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.building.api.BuildingClusterController;
import com.uip.backend.building.api.dto.BuildingResponse;
import com.uip.backend.building.domain.BuildingCluster;
import com.uip.backend.building.service.BuildingClusterService;
import com.uip.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for BuildingClusterController (Sprint 11 — security fix BC-01).
 *
 * Verifies:
 * - listByCluster() requires X-Tenant-ID header (missing → 400 Bad Request)
 * - listByCluster() filters by tenantId — cross-tenant buildings are excluded
 * - list() and getByCode() also enforce X-Tenant-ID
 *
 * Uses standalone MockMvc (no Spring Security filter chain) to test
 * controller logic in isolation.
 *
 * For full security filter chain tests see CrossBuildingConcurrentRLSIT.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BuildingClusterController")
class BuildingClusterControllerTest {

    @Mock
    private BuildingClusterService service;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private BuildingClusterController controller;

    private MockMvc mockMvc;

    private static final String CLUSTER_ID     = "cluster-hcm-01";
    private static final String TENANT_A       = "tenant-hcm";
    private static final String TENANT_B       = "tenant-hanoi";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── listByCluster() — multi-tenancy enforcement ────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/buildings/clusters/{clusterId} — tenant isolation")
    class ListByCluster {

        @Test
        @DisplayName("Returns 400 when X-Tenant-ID header is absent")
        void missingTenantHeader_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/buildings/clusters/{id}", CLUSTER_ID)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns only buildings belonging to the requesting tenant")
        void withValidTenantHeader_returnsOnlyTenantOwnedBuildings() throws Exception {
            BuildingCluster bldA = buildingFor(TENANT_A, CLUSTER_ID);
            BuildingCluster bldB = buildingFor(TENANT_B, CLUSTER_ID);   // cross-tenant
            when(service.findByCluster(CLUSTER_ID)).thenReturn(List.of(bldA, bldB));

            mockMvc.perform(get("/api/v1/buildings/clusters/{id}", CLUSTER_ID)
                            .header("X-Tenant-ID", TENANT_A)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].tenantId", is(TENANT_A)));
        }

        @Test
        @DisplayName("Returns empty list when all cluster buildings belong to another tenant")
        void tenantARequestForTenantBBuildings_returnsEmptyList() throws Exception {
            BuildingCluster bldB = buildingFor(TENANT_B, CLUSTER_ID);
            when(service.findByCluster(CLUSTER_ID)).thenReturn(List.of(bldB));

            mockMvc.perform(get("/api/v1/buildings/clusters/{id}", CLUSTER_ID)
                            .header("X-Tenant-ID", TENANT_A)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Returns all tenant buildings when cluster contains only own buildings")
        void tenantOwnsAllBuildings_returnsAll() throws Exception {
            BuildingCluster bld1 = buildingFor(TENANT_A, CLUSTER_ID, "BLD-001");
            BuildingCluster bld2 = buildingFor(TENANT_A, CLUSTER_ID, "BLD-002");
            when(service.findByCluster(CLUSTER_ID)).thenReturn(List.of(bld1, bld2));

            mockMvc.perform(get("/api/v1/buildings/clusters/{id}", CLUSTER_ID)
                            .header("X-Tenant-ID", TENANT_A)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("Service findByCluster is called once with the correct clusterId")
        void serviceIsCalledWithCorrectClusterId() throws Exception {
            when(service.findByCluster(CLUSTER_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/buildings/clusters/{id}", CLUSTER_ID)
                            .header("X-Tenant-ID", TENANT_A));

            verify(service, times(1)).findByCluster(CLUSTER_ID);
        }
    }

    // ── list() — basic tenant enforcement ─────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/buildings — list all by tenant")
    class List_ {

        @Test
        @DisplayName("Returns 400 when X-Tenant-ID header is absent")
        void missingTenantHeader_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/buildings")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 200 with tenant buildings when header is present")
        void presentHeader_returns200() throws Exception {
            when(service.findByTenant(TENANT_A)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/buildings")
                            .header("X-Tenant-ID", TENANT_A)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    // ── getByCode() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/buildings/{code} — get by building code")
    class GetByCode {

        @Test
        @DisplayName("Returns 400 when X-Tenant-ID header is absent")
        void missingTenantHeader_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/buildings/{code}", "BLD-001")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Delegates to service with correct tenantId and buildingCode")
        void delegatesToServiceCorrectly() throws Exception {
            BuildingCluster bld = buildingFor(TENANT_A, CLUSTER_ID, "BLD-001");
            when(service.findByCode(TENANT_A, "BLD-001")).thenReturn(bld);

            mockMvc.perform(get("/api/v1/buildings/{code}", "BLD-001")
                            .header("X-Tenant-ID", TENANT_A)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.buildingCode", is("BLD-001")))
                    .andExpect(jsonPath("$.tenantId", is(TENANT_A)));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BuildingCluster buildingFor(String tenantId, String clusterId) {
        return buildingFor(tenantId, clusterId, "BLD-" + tenantId.substring(0, 3).toUpperCase());
    }

    private BuildingCluster buildingFor(String tenantId, String clusterId, String code) {
        return BuildingCluster.builder()
                .id(UUID.randomUUID())
                .buildingCode(code)
                .buildingName("Building " + code)
                .tenantId(tenantId)
                .clusterId(clusterId)
                .floorCount(5)
                .totalAreaM2(2000.0)
                .isActive(true)
                .build();
    }
}
