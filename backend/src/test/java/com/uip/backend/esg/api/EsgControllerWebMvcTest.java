package com.uip.backend.esg.api;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.esg.api.dto.EsgMetricDto;
import com.uip.backend.esg.api.dto.EsgReportDto;
import com.uip.backend.esg.api.dto.EsgSummaryDto;
import com.uip.backend.esg.service.EsgService;
import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.filter.TenantContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvc slice tests for {@link EsgController}.
 *
 * Both {@link JwtAuthenticationFilter} and {@link TenantContextFilter} are excluded so that
 * we can use {@link MockedStatic} to control what {@code TenantContext.getCurrentTenant()}
 * returns during each request — enabling precise tenant-isolation assertions without a real JWT.
 */
@WebMvcTest(
    controllers = EsgController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantContextFilter.class)
    }
)
@Import(EsgControllerWebMvcTest.MethodSecurityConfig.class)
@DisplayName("EsgController — WebMvc")
class EsgControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @MockBean  EsgService esgService;

    private static final String TENANT_HCM     = "hcm";
    private static final String TENANT_DEFAULT = "default";

    // ─── GET /summary ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /esg/summary — authenticated → delegates tenantId from TenantContext")
    void getSummary_authenticated_delegatesTenantId() throws Exception {
        EsgSummaryDto dto = EsgSummaryDto.builder()
            .period("QUARTERLY").year(2026).quarter(1)
            .totalEnergyKwh(1000.0).totalWaterM3(500.0)
            .totalCarbonTco2e(200.0).totalWasteTons(50.0)
            .build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(esgService.getSummary(eq(TENANT_HCM), anyString(), anyInt(), anyInt())).thenReturn(dto);

            mockMvc.perform(get("/api/v1/esg/summary").param("year", "2026").param("quarter", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("QUARTERLY"))
                .andExpect(jsonPath("$.totalEnergyKwh").value(1000.0))
                .andExpect(jsonPath("$.totalCarbonTco2e").value(200.0));

            verify(esgService).getSummary(eq(TENANT_HCM), anyString(), eq(2026), eq(1));
        }
    }

    @Test
    @DisplayName("GET /esg/summary — unauthenticated → 401/403")
    void getSummary_unauthenticated_rejects() throws Exception {
        mockMvc.perform(get("/api/v1/esg/summary"))
            .andExpect(status().is(greaterThanOrEqualTo(401)));

        verifyNoInteractions(esgService);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /esg/summary — tenant isolation: default tenant delegates only default id")
    void getSummary_tenantIsolation_differentTenantsDelegateCorrectId() throws Exception {
        EsgSummaryDto dto = EsgSummaryDto.builder().period("QUARTERLY").year(2026).quarter(1)
            .totalEnergyKwh(300.0).build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_DEFAULT);
            when(esgService.getSummary(eq(TENANT_DEFAULT), anyString(), anyInt(), anyInt())).thenReturn(dto);

            mockMvc.perform(get("/api/v1/esg/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEnergyKwh").value(300.0));

            verify(esgService).getSummary(eq(TENANT_DEFAULT), anyString(), anyInt(), anyInt());
            verify(esgService, never()).getSummary(eq(TENANT_HCM), anyString(), anyInt(), anyInt());
        }
    }

    // ─── GET /energy ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /esg/energy — returns metric list with tenantId forwarded")
    void getEnergy_authenticated_returnsList() throws Exception {
        EsgMetricDto metric = EsgMetricDto.builder()
            .sourceId("SENSOR-HCM-001").metricType("ENERGY")
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .value(450.0).unit("kWh").build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(esgService.getEnergyData(eq(TENANT_HCM), any(), any(), isNull())).thenReturn(List.of(metric));

            mockMvc.perform(get("/api/v1/esg/energy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceId").value("SENSOR-HCM-001"))
                .andExpect(jsonPath("$[0].value").value(450.0));

            verify(esgService).getEnergyData(eq(TENANT_HCM), any(), any(), isNull());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /esg/energy?building=B1 — building param forwarded")
    void getEnergy_withBuildingFilter_forwardsBuildingId() throws Exception {
        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(esgService.getEnergyData(eq(TENANT_HCM), any(), any(), eq("B1"))).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/esg/energy").param("building", "B1"))
                .andExpect(status().isOk());

            verify(esgService).getEnergyData(eq(TENANT_HCM), any(), any(), eq("B1"));
        }
    }

    // ─── GET /carbon ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /esg/carbon — returns metric list with tenantId forwarded")
    void getCarbon_authenticated_returnsList() throws Exception {
        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(esgService.getCarbonData(eq(TENANT_HCM), any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/esg/carbon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

            verify(esgService).getCarbonData(eq(TENANT_HCM), any(), any());
        }
    }

    // ─── POST /reports/generate ───────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("POST /esg/reports/generate — OPERATOR → 202 Accepted with tenantId")
    void generateReport_asOperator_returns202() throws Exception {
        UUID reportId = UUID.randomUUID();
        EsgReportDto dto = EsgReportDto.builder()
            .id(reportId).periodType("QUARTERLY").year(2026).quarter(1)
            .status("PENDING").build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(esgService.triggerReportGeneration(eq(TENANT_HCM), anyString(), anyInt(), anyInt())).thenReturn(dto);

            mockMvc.perform(post("/api/v1/esg/reports/generate")
                    .with(csrf())
                    .param("year", "2026").param("quarter", "1")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").value(reportId.toString()));

            verify(esgService).triggerReportGeneration(eq(TENANT_HCM), anyString(), eq(2026), eq(1));
        }
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    @DisplayName("POST /esg/reports/generate — CITIZEN → 403 Forbidden")
    void generateReport_asCitizen_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/esg/reports/generate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());

        verifyNoInteractions(esgService);
    }

    // ─── GET /reports/{id}/status ─────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /esg/reports/{id}/status — authenticated → 200 with tenantId forwarded")
    void getReportStatus_authenticated_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        EsgReportDto dto = EsgReportDto.builder()
            .id(id).status("DONE").periodType("QUARTERLY").year(2026).quarter(1).build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(esgService.getReportStatus(eq(TENANT_HCM), eq(id))).thenReturn(dto);

            mockMvc.perform(get("/api/v1/esg/reports/{id}/status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

            verify(esgService).getReportStatus(eq(TENANT_HCM), eq(id));
        }
    }

    @Test
    @DisplayName("GET /esg/reports/{id}/status — unauthenticated → 401/403")
    void getReportStatus_unauthenticated_rejects() throws Exception {
        mockMvc.perform(get("/api/v1/esg/reports/{id}/status", UUID.randomUUID()))
            .andExpect(status().is(greaterThanOrEqualTo(401)));
    }
}
