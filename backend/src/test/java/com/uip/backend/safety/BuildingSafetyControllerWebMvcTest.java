package com.uip.backend.safety;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import com.uip.backend.safety.controller.BuildingSafetyController;
import com.uip.backend.safety.model.SafetyScore;
import com.uip.backend.safety.service.BuildingSafetyService;
import com.uip.backend.tenant.filter.TenantContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvc tests for BuildingSafetyController.
 *
 * Security matrix:
 *   - Unauthenticated → 401
 *   - Authenticated → 200 with correct JSON shape
 *   - vibration readings: empty list returns 200 []
 */
@WebMvcTest(
        controllers = BuildingSafetyController.class,
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantContextFilter.class),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
        }
)
@Import(BuildingSafetyControllerWebMvcTest.MethodSecurityConfig.class)
@DisplayName("BuildingSafetyController — WebMvc")
class BuildingSafetyControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @MockBean  BuildingSafetyService buildingSafetyService;
    @MockBean @SuppressWarnings("unused") TenantRateLimiter tenantRateLimiter;

    private static final SafetyScore SAFE_SCORE =
            new SafetyScore("BLDG-001", 95, "SAFE", Instant.parse("2026-06-02T10:00:00Z"), 0);
    private static final SafetyScore CRITICAL_SCORE =
            new SafetyScore("BLDG-001", 40, "CRITICAL", Instant.parse("2026-06-02T10:00:00Z"), 2);

    // ─── GET /buildings/{id}/safety ──────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /buildings/BLDG-001/safety — authenticated returns 200 with score")
    void getSafetyScore_authenticated_returns200() throws Exception {
        when(buildingSafetyService.getSafetyScore("BLDG-001")).thenReturn(SAFE_SCORE);

        mockMvc.perform(get("/api/v1/buildings/BLDG-001/safety"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(95))
                .andExpect(jsonPath("$.status").value("SAFE"))
                .andExpect(jsonPath("$.activeAlerts").value(0))
                .andExpect(jsonPath("$.lastUpdated").exists());
    }

    @Test
    @DisplayName("GET /buildings/BLDG-001/safety — unauthenticated returns 401")
    void getSafetyScore_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/buildings/BLDG-001/safety"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /buildings/{id}/safety — CRITICAL score returned correctly")
    void getSafetyScore_critical_returnsCorrectStatus() throws Exception {
        when(buildingSafetyService.getSafetyScore("BLDG-001")).thenReturn(CRITICAL_SCORE);

        mockMvc.perform(get("/api/v1/buildings/BLDG-001/safety"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(40))
                .andExpect(jsonPath("$.status").value("CRITICAL"))
                .andExpect(jsonPath("$.activeAlerts").value(2));
    }

    // ─── GET /buildings/{id}/vibration/readings ──────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /buildings/{id}/vibration/readings — authenticated returns 200 []")
    void getVibrationReadings_authenticated_returns200() throws Exception {
        when(buildingSafetyService.getVibrationReadings(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/buildings/BLDG-001/vibration/readings")
                        .param("sensorType", "STRUCTURAL_VIBRATION")
                        .param("range", "24h"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("GET /buildings/{id}/vibration/readings — unauthenticated returns 401")
    void getVibrationReadings_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/buildings/BLDG-001/vibration/readings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /buildings/{id}/vibration/readings — default params accepted")
    void getVibrationReadings_defaultParams_returns200() throws Exception {
        when(buildingSafetyService.getVibrationReadings(eq("BLDG-001"),
                eq("STRUCTURAL_VIBRATION"), eq("24h")))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/buildings/BLDG-001/vibration/readings"))
                .andExpect(status().isOk());
    }
}
