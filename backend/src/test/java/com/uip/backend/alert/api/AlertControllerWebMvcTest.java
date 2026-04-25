package com.uip.backend.alert.api;

import com.uip.backend.alert.api.dto.AlertEventDto;
import com.uip.backend.alert.service.AlertService;
import com.uip.backend.auth.config.JwtAuthenticationFilter;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = AlertController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = JwtAuthenticationFilter.class)
)
@Import(AlertControllerWebMvcTest.MethodSecurityConfig.class)
@DisplayName("AlertController — WebMvc")
class AlertControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @MockBean AlertService alertService;

    private AlertEventDto buildDto(UUID id, String status) {
        return AlertEventDto.builder()
            .id(id).sensorId("ENV-001").module("environment")
            .measureType("AQI").value(210.0).threshold(200.0)
            .severity("CRITICAL").status(status).detectedAt(Instant.now())
            .build();
    }

    @Test
    @WithMockUser
    @DisplayName("GET /notifications — authenticated → 200")
    void getPublicNotifications_authenticated_returns200() throws Exception {
        when(alertService.getPublicNotifications(anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(buildDto(UUID.randomUUID(), "OPEN"))));

        mockMvc.perform(get("/api/v1/alerts/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /notifications — unauthenticated → 401/403")
    void getPublicNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/notifications"))
            .andExpect(status().is(org.hamcrest.Matchers.greaterThanOrEqualTo(401)));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("PUT /{id}/escalate — OPERATOR → 200")
    void escalate_asOperator_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.escalateAlert(eq(id), anyString(), any()))
            .thenReturn(buildDto(id, "ESCALATED"));

        mockMvc.perform(put("/api/v1/alerts/{id}/escalate", id)
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ESCALATED"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("PUT /{id}/escalate — alert not found → 404 (GlobalExceptionHandler maps EntityNotFoundException)")
    void escalate_alertNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.escalateAlert(eq(id), anyString(), any()))
            .thenThrow(new EntityNotFoundException("Alert not found: " + id));

        mockMvc.perform(put("/api/v1/alerts/{id}/escalate", id)
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("PUT /{id}/escalate — VIEWER role → 403 Forbidden")
    void escalate_asViewer_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/alerts/{id}/escalate", UUID.randomUUID())
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isForbidden());
    }
}
