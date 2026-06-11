package com.uip.analytics.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.analytics.api.dto.*;
import com.uip.analytics.api.dto.AqiTrendResponse.AqiDataPoint;
import com.uip.analytics.api.dto.EmissionsAggregateResponse.TenantEmissionsBreakdown;
import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import com.uip.analytics.config.SecurityConfig;
import com.uip.analytics.service.AqiTrendService;
import com.uip.analytics.service.EmissionsAggregateService;
import com.uip.analytics.service.EnergyAggregateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AnalyticsController — @WebMvcTest")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnergyAggregateService energyService;

    @MockBean
    private EmissionsAggregateService emissionsService;

    @MockBean
    private AqiTrendService aqiTrendService;

    private static final String TENANT_A = "tenant-a";
    private static final long FROM = 1000L;
    private static final long TO = 2000L;

    @Nested
    @DisplayName("POST /api/v1/analytics/energy-aggregate")
    class EnergyAggregate {

        @Test
        @DisplayName("200 — valid request with ADMIN role returns aggregated data")
        void validRequest_adminRole_returns200() throws Exception {
            var response = new EnergyAggregateResponse(
                    TENANT_A, FROM, TO, 450.0, 200.0, 0.95,
                    List.of(new BuildingEnergyBreakdown("B1", 300.0, 200.0))
            );
            when(energyService.aggregate(any())).thenReturn(response);

            var request = new EnergyAggregateRequest(TENANT_A, List.of("B1"), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/energy-aggregate")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                    .andExpect(jsonPath("$.totalKwh").value(450.0))
                    .andExpect(jsonPath("$.peakDemandKw").value(200.0))
                    .andExpect(jsonPath("$.averagePowerFactor").value(0.95))
                    .andExpect(jsonPath("$.buildings[0].buildingId").value("B1"))
                    .andExpect(jsonPath("$.buildings[0].totalKwh").value(300.0));
        }

        @Test
        @DisplayName("200 — OPERATOR role has access")
        void operatorRole_hasAccess() throws Exception {
            when(energyService.aggregate(any())).thenReturn(
                    new EnergyAggregateResponse(TENANT_A, FROM, TO, 0.0, 0.0, 1.0, List.of())
            );

            var request = new EnergyAggregateRequest(TENANT_A, List.of(), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/energy-aggregate")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("200 — ANALYTICS_READ role has access")
        void analyticsReadRole_hasAccess() throws Exception {
            when(energyService.aggregate(any())).thenReturn(
                    new EnergyAggregateResponse(TENANT_A, FROM, TO, 0.0, 0.0, 1.0, List.of())
            );

            var request = new EnergyAggregateRequest(TENANT_A, List.of(), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/energy-aggregate")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYTICS_READ")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("401 — unauthenticated request is rejected")
        void unauthenticated_returns401() throws Exception {
            var request = new EnergyAggregateRequest(TENANT_A, List.of(), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/energy-aggregate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — insufficient role is forbidden")
        void insufficientRole_returns403() throws Exception {
            var request = new EnergyAggregateRequest(TENANT_A, List.of(), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/energy-aggregate")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("403 — cross-tenant request is forbidden when JWT has tenant_id")
        void crossTenantRequest_returns403() throws Exception {
            // Simulate a Keycloak-authenticated user with tenant_id in details
            var auth = new UsernamePasswordAuthenticationToken(
                    "user-1", null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            auth.setDetails(java.util.Map.of("tenant_id", "tenant-b"));
            SecurityContextHolder.getContext().setAuthentication(auth);

            // Request tenant-a data while JWT says tenant-b
            var request = new EnergyAggregateRequest(TENANT_A, List.of(), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/energy-aggregate")
                            .with(authentication(auth))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            SecurityContextHolder.clearContext();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/analytics/emissions-aggregate")
    class EmissionsAggregate {

        @Test
        @DisplayName("200 — valid request returns emissions data")
        void validRequest_returns200() throws Exception {
            var response = new EmissionsAggregateResponse(
                    TENANT_A, FROM, TO, 800.0,
                    List.of(new TenantEmissionsBreakdown("B1", 500.0, 20.5))
            );
            when(emissionsService.aggregate(any())).thenReturn(response);

            var request = new EmissionsAggregateRequest(TENANT_A, List.of("B1"), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/emissions-aggregate")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                    .andExpect(jsonPath("$.totalCo2Kg").value(800.0))
                    .andExpect(jsonPath("$.buildings[0].buildingId").value("B1"))
                    .andExpect(jsonPath("$.buildings[0].totalCo2Kg").value(500.0))
                    .andExpect(jsonPath("$.buildings[0].avgCo2PerHour").value(20.5));
        }

        @Test
        @DisplayName("401 — unauthenticated request is rejected")
        void unauthenticated_returns401() throws Exception {
            var request = new EmissionsAggregateRequest(TENANT_A, List.of(), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/emissions-aggregate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/analytics/aqi-trend")
    class AqiTrend {

        @Test
        @DisplayName("200 — valid request returns AQI trend data")
        void validRequest_returns200() throws Exception {
            var response = new AqiTrendResponse(TENANT_A, List.of(
                    new AqiDataPoint("B1", 1000L, 45.5, 60.0),
                    new AqiDataPoint("B1", 3600L, 50.0, 65.0)
            ));
            when(aqiTrendService.getTrend(any())).thenReturn(response);

            var request = new AqiTrendRequest(TENANT_A, List.of("B1"), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/aqi-trend")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenantId").value(TENANT_A))
                    .andExpect(jsonPath("$.dataPoints").isArray())
                    .andExpect(jsonPath("$.dataPoints.length()").value(2))
                    .andExpect(jsonPath("$.dataPoints[0].buildingId").value("B1"))
                    .andExpect(jsonPath("$.dataPoints[0].avgAqi").value(45.5))
                    .andExpect(jsonPath("$.dataPoints[0].maxAqi").value(60.0));
        }

        @Test
        @DisplayName("200 — empty data points returned as empty array")
        void emptyData_returnsEmptyArray() throws Exception {
            var response = new AqiTrendResponse(TENANT_A, List.of());
            when(aqiTrendService.getTrend(any())).thenReturn(response);

            var request = new AqiTrendRequest(TENANT_A, List.of(), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/aqi-trend")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataPoints").isArray())
                    .andExpect(jsonPath("$.dataPoints.length()").value(0));
        }

        @Test
        @DisplayName("401 — unauthenticated request is rejected")
        void unauthenticated_returns401() throws Exception {
            var request = new AqiTrendRequest(TENANT_A, List.of(), FROM, TO);

            mockMvc.perform(post("/api/v1/analytics/aqi-trend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
