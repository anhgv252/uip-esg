package com.uip.backend.billing.api;

import com.uip.backend.billing.dto.BuildingRoiResponse;
import com.uip.backend.billing.dto.TenantRoiSummary;
import com.uip.backend.billing.service.RoiCalculationService;
import com.uip.backend.tenant.context.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5-3 T06: Unit tests for RoiController.
 * 
 * Test cases:
 * 1. GET /api/v1/roi/building/{id} → 200 OK with cost breakdown
 * 2. GET /api/v1/roi/summary → 200 OK with tenant summary
 * 3. Building endpoint with ROLE_OPERATOR → 200 OK
 * 4. Summary endpoint with ROLE_OPERATOR → 403 Forbidden (requires ADMIN/TENANT_ADMIN)
 * 5. Month parameter defaults to current month
 * 6. Unauthenticated request → 401 Unauthorized
 */
@WebMvcTest(RoiController.class)
class RoiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoiCalculationService roiCalculationService;

    private static final String TENANT_ID = "tenant-001";
    private static final String BUILDING_ID = "BLDG-001";
    private static final String MONTH = "2026-06";

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_ID);
    }

    @Test
    @DisplayName("Test: GET /api/v1/roi/building/{id} → 200 OK with cost breakdown")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testGetBuildingRoi() throws Exception {
        // Given
        BuildingRoiResponse response = BuildingRoiResponse.builder()
                .buildingId(BUILDING_ID)
                .month(MONTH)
                .costs(BuildingRoiResponse.CostBreakdown.builder()
                        .baseFeeVnd(2_000_000L)
                        .aiTokensUsed(125_000L)
                        .aiOverageTokens(25_000L)
                        .aiOverageCostVnd(1_250L)
                        .totalCostVnd(2_001_250L)
                        .sensorReadings(8640L)
                        .alertsGenerated(14L)
                        .build())
                .savings(BuildingRoiResponse.SavingsBreakdown.builder()
                        .manualOpsCostVnd(8_500_000L)
                        .automationSavingsVnd(6_498_750L)
                        .paybackMonths(new BigDecimal("3.1"))
                        .co2SavedKg(new BigDecimal("42.5"))
                        .build())
                .comparisonChart(List.of())
                .build();

        when(roiCalculationService.calculateBuildingRoi(eq(TENANT_ID), eq(BUILDING_ID), eq(MONTH)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/roi/building/{buildingId}", BUILDING_ID)
                        .param("month", MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildingId").value(BUILDING_ID))
                .andExpect(jsonPath("$.month").value(MONTH))
                .andExpect(jsonPath("$.costs.baseFeeVnd").value(2_000_000))
                .andExpect(jsonPath("$.costs.aiTokensUsed").value(125_000))
                .andExpect(jsonPath("$.costs.totalCostVnd").value(2_001_250))
                .andExpect(jsonPath("$.savings.automationSavingsVnd").value(6_498_750));
    }

    @Test
    @DisplayName("Test: GET /api/v1/roi/summary → 200 OK with tenant summary")
    @WithMockUser(username = "tenant-admin", roles = {"TENANT_ADMIN"})
    void testGetTenantRoiSummary() throws Exception {
        // Given
        TenantRoiSummary summary = TenantRoiSummary.builder()
                .tenantId(TENANT_ID)
                .month(MONTH)
                .buildingCount(5)
                .totalCostVnd(10_000_000L)
                .totalSavingsVnd(32_500_000L)
                .avgPaybackMonths(new BigDecimal("3.0"))
                .totalCo2SavedKg(new BigDecimal("212.5"))
                .buildings(List.of())
                .build();

        when(roiCalculationService.calculateTenantRoiSummary(eq(TENANT_ID), eq(MONTH)))
                .thenReturn(summary);

        // When & Then
        mockMvc.perform(get("/api/v1/roi/summary")
                        .param("month", MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.month").value(MONTH))
                .andExpect(jsonPath("$.buildingCount").value(5))
                .andExpect(jsonPath("$.totalCostVnd").value(10_000_000))
                .andExpect(jsonPath("$.totalSavingsVnd").value(32_500_000));
    }

    @Test
    @DisplayName("Test: Building endpoint with ROLE_OPERATOR → 200 OK")
    @WithMockUser(username = "operator", roles = {"OPERATOR"})
    void testBuildingEndpointWithOperatorRole() throws Exception {
        // Given
        BuildingRoiResponse response = BuildingRoiResponse.builder()
                .buildingId(BUILDING_ID)
                .month(MONTH)
                .costs(BuildingRoiResponse.CostBreakdown.builder()
                        .baseFeeVnd(2_000_000L)
                        .aiTokensUsed(0L)
                        .aiOverageTokens(0L)
                        .aiOverageCostVnd(0L)
                        .totalCostVnd(2_000_000L)
                        .sensorReadings(0L)
                        .alertsGenerated(0L)
                        .build())
                .savings(BuildingRoiResponse.SavingsBreakdown.builder()
                        .manualOpsCostVnd(8_500_000L)
                        .automationSavingsVnd(6_500_000L)
                        .paybackMonths(BigDecimal.ZERO)
                        .co2SavedKg(BigDecimal.ZERO)
                        .build())
                .comparisonChart(List.of())
                .build();

        when(roiCalculationService.calculateBuildingRoi(any(), eq(BUILDING_ID), any()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/roi/building/{buildingId}", BUILDING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildingId").value(BUILDING_ID));
    }

    @Test
    @DisplayName("Test: Summary endpoint with ROLE_OPERATOR → 403 Forbidden")
    @WithMockUser(username = "operator", roles = {"OPERATOR"})
    void testSummaryEndpointWithOperatorRoleForbidden() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/roi/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test: Month parameter defaults to current month")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testMonthParameterDefaults() throws Exception {
        // Given: No month parameter
        BuildingRoiResponse response = BuildingRoiResponse.builder()
                .buildingId(BUILDING_ID)
                .month("2026-06") // Service will set current month
                .costs(BuildingRoiResponse.CostBreakdown.builder()
                        .baseFeeVnd(2_000_000L)
                        .aiTokensUsed(0L)
                        .aiOverageTokens(0L)
                        .aiOverageCostVnd(0L)
                        .totalCostVnd(2_000_000L)
                        .sensorReadings(0L)
                        .alertsGenerated(0L)
                        .build())
                .savings(BuildingRoiResponse.SavingsBreakdown.builder()
                        .manualOpsCostVnd(8_500_000L)
                        .automationSavingsVnd(6_500_000L)
                        .paybackMonths(BigDecimal.ZERO)
                        .co2SavedKg(BigDecimal.ZERO)
                        .build())
                .comparisonChart(List.of())
                .build();

        when(roiCalculationService.calculateBuildingRoi(any(), eq(BUILDING_ID), eq(null)))
                .thenReturn(response);

        // When & Then: No month parameter
        mockMvc.perform(get("/api/v1/roi/building/{buildingId}", BUILDING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").exists()); // Month should be set by service
    }

    @Test
    @DisplayName("Test: Unauthenticated request → 401 Unauthorized")
    void testUnauthenticatedRequestUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/roi/building/{buildingId}", BUILDING_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test: Building endpoint with ROLE_TENANT_ADMIN → 200 OK")
    @WithMockUser(username = "tenant-admin", roles = {"TENANT_ADMIN"})
    void testBuildingEndpointWithTenantAdminRole() throws Exception {
        // Given
        BuildingRoiResponse response = BuildingRoiResponse.builder()
                .buildingId(BUILDING_ID)
                .month(MONTH)
                .costs(BuildingRoiResponse.CostBreakdown.builder()
                        .baseFeeVnd(2_000_000L)
                        .aiTokensUsed(0L)
                        .aiOverageTokens(0L)
                        .aiOverageCostVnd(0L)
                        .totalCostVnd(2_000_000L)
                        .sensorReadings(0L)
                        .alertsGenerated(0L)
                        .build())
                .savings(BuildingRoiResponse.SavingsBreakdown.builder()
                        .manualOpsCostVnd(8_500_000L)
                        .automationSavingsVnd(6_500_000L)
                        .paybackMonths(BigDecimal.ZERO)
                        .co2SavedKg(BigDecimal.ZERO)
                        .build())
                .comparisonChart(List.of())
                .build();

        when(roiCalculationService.calculateBuildingRoi(any(), eq(BUILDING_ID), any()))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/roi/building/{buildingId}", BUILDING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildingId").value(BUILDING_ID));
    }
}
