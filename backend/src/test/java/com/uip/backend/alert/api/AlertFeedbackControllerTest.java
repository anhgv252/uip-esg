package com.uip.backend.alert.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.alert.api.dto.AlertEventDto;
import com.uip.backend.alert.service.AlertService;
import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AlertController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
        }
)
@Import(AlertFeedbackControllerTest.MethodSecurityConfig.class)
@DisplayName("AlertController — Feedback endpoint")
class AlertFeedbackControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AlertService alertService;
    @MockBean @SuppressWarnings("unused") TenantRateLimiter tenantRateLimiter;

    private static final String FEEDBACK_URL = "/api/v1/alerts/{id}/feedback";

    private AlertEventDto buildDtoWithFeedback(UUID id, Boolean correct, String comment, String feedbackBy) {
        return AlertEventDto.builder()
                .id(id).sensorId("ENV-001").module("environment")
                .measureType("AQI").value(210.0).threshold(200.0)
                .severity("CRITICAL").status("OPEN").detectedAt(Instant.now())
                .feedbackCorrect(correct)
                .feedbackComment(comment)
                .feedbackBy(feedbackBy)
                .feedbackAt(Instant.now())
                .build();
    }

    // ─── OPERATOR can submit feedback ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "operator1", roles = "OPERATOR")
    @DisplayName("CT-FB-01: POST /{id}/feedback — OPERATOR → 200 with feedback fields")
    void submitFeedback_asOperator_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.recordFeedback(eq(id), eq("operator1"), eq(true), eq("Confirmed accurate")))
                .thenReturn(buildDtoWithFeedback(id, true, "Confirmed accurate", "operator1"));

        mockMvc.perform(post(FEEDBACK_URL, id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correct\":true,\"comment\":\"Confirmed accurate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackCorrect").value(true))
                .andExpect(jsonPath("$.feedbackComment").value("Confirmed accurate"))
                .andExpect(jsonPath("$.feedbackBy").value("operator1"));

        verify(alertService).recordFeedback(eq(id), eq("operator1"), eq(true), eq("Confirmed accurate"));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("CT-FB-02: POST /{id}/feedback — ADMIN → 200")
    void submitFeedback_asAdmin_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.recordFeedback(eq(id), eq("admin1"), eq(false), anyString()))
                .thenReturn(buildDtoWithFeedback(id, false, "Wrong prediction", "admin1"));

        mockMvc.perform(post(FEEDBACK_URL, id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correct\":false,\"comment\":\"Wrong prediction\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackCorrect").value(false));
    }

    // ─── CITIZEN forbidden ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "CITIZEN")
    @DisplayName("CT-FB-03: POST /{id}/feedback — CITIZEN → 403 Forbidden")
    void submitFeedback_asCitizen_returns403() throws Exception {
        mockMvc.perform(post(FEEDBACK_URL, UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correct\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("CT-FB-04: POST /{id}/feedback — VIEWER → 403 Forbidden")
    void submitFeedback_asViewer_returns403() throws Exception {
        mockMvc.perform(post(FEEDBACK_URL, UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correct\":true}"))
                .andExpect(status().isForbidden());
    }

    // ─── Alert not found ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("CT-FB-05: POST /{id}/feedback — unknown alert → 404")
    void submitFeedback_unknownAlert_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.recordFeedback(eq(id), anyString(), any(), any()))
                .thenThrow(new EntityNotFoundException("Alert not found: " + id));

        mockMvc.perform(post(FEEDBACK_URL, id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correct\":true,\"comment\":\"test\"}"))
                .andExpect(status().isNotFound());
    }

    // ─── Feedback null values gracefully handled ──────────────────────────────

    @Test
    @WithMockUser(username = "op2", roles = "OPERATOR")
    @DisplayName("CT-FB-06: POST /{id}/feedback — null correct field accepted")
    void submitFeedback_nullCorrect_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.recordFeedback(eq(id), eq("op2"), isNull(), eq("No opinion")))
                .thenReturn(buildDtoWithFeedback(id, null, "No opinion", "op2"));

        mockMvc.perform(post(FEEDBACK_URL, id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correct\":null,\"comment\":\"No opinion\"}"))
                .andExpect(status().isOk());
    }
}
