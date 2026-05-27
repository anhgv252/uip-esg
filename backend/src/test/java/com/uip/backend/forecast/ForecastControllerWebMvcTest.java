package com.uip.backend.forecast;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvc tests for ForecastController.
 *
 * Security matrix (ADR-032 D4):
 *   - No tenant context → 403
 *   - Valid tenant → 200 (or downstream error)
 *   - horizonDays boundary: 0, -1, 91, 365
 */
@WebMvcTest(
    controllers = ForecastController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantContextFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
    }
)
@Import(ForecastControllerWebMvcTest.MethodSecurityConfig.class)
@DisplayName("ForecastController — WebMvc")
class ForecastControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @MockBean  ForecastService forecastService;
    @MockBean @SuppressWarnings("unused") TenantRateLimiter tenantRateLimiter;

    private static final ForecastResult STUB_RESULT = new ForecastResult(
            "hcm", "B1", "ARIMA", false, 0.08,
            Collections.emptyList(), Instant.now()
    );

    @Test
    @WithMockUser
    @DisplayName("GET /forecast/energy — valid request returns 200")
    void forecastEnergy_validRequest() throws Exception {
        when(forecastService.forecast(anyString(), eq("B1"), anyInt())).thenReturn(STUB_RESULT);

        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn("hcm");

            mockMvc.perform(get("/api/v1/forecast/energy")
                            .param("buildingId", "B1")
                            .param("horizonDays", "30"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /forecast/energy — no tenant returns 403")
    void forecastEnergy_noTenant_returns403() throws Exception {
        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn(null);

            mockMvc.perform(get("/api/v1/forecast/energy")
                            .param("buildingId", "B1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /forecast/energy — blank tenant returns 403")
    void forecastEnergy_blankTenant_returns403() throws Exception {
        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn("   ");

            mockMvc.perform(get("/api/v1/forecast/energy")
                            .param("buildingId", "B1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /forecast/energy — horizonDays=0 returns validation error")
    void forecastEnergy_horizonZero_returns400() throws Exception {
        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn("hcm");

            mockMvc.perform(get("/api/v1/forecast/energy")
                            .param("buildingId", "B1")
                            .param("horizonDays", "0"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /forecast/energy — horizonDays=-1 returns validation error")
    void forecastEnergy_horizonNegative_returns400() throws Exception {
        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn("hcm");

            mockMvc.perform(get("/api/v1/forecast/energy")
                            .param("buildingId", "B1")
                            .param("horizonDays", "-1"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /forecast/energy — horizonDays=91 returns validation error")
    void forecastEnergy_horizonOver90_returns400() throws Exception {
        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn("hcm");

            mockMvc.perform(get("/api/v1/forecast/energy")
                            .param("buildingId", "B1")
                            .param("horizonDays", "91"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /forecast/energy — horizonDays=365 returns validation error")
    void forecastEnergy_horizon365_returns400() throws Exception {
        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn("hcm");

            mockMvc.perform(get("/api/v1/forecast/energy")
                            .param("buildingId", "B1")
                            .param("horizonDays", "365"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /forecast/energy — forecast unavailable returns 503")
    void forecastEnergy_serviceUnavailable_returns503() throws Exception {
        when(forecastService.forecast(anyString(), anyString(), anyInt()))
                .thenThrow(new ForecastServiceUnavailableException("Connection refused", null));

        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn("hcm");

            mockMvc.perform(get("/api/v1/forecast/energy")
                            .param("buildingId", "B1"))
                    .andExpect(status().isServiceUnavailable());
        }
    }
}
