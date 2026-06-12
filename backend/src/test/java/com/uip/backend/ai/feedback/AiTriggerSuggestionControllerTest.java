package com.uip.backend.ai.feedback;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M4-COR-07: WebMvc slice test for {@link AiTriggerSuggestionController}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Endpoint {@code GET /api/v1/ai/trigger-suggestions} is ADMIN-gated ({@code @PreAuthorize}).</li>
 *   <li>Non-ADMIN roles receive 403.</li>
 *   <li>Anonymous receives 401.</li>
 *   <li>ADMIN receives 200 with the suggestion list (or empty list when data is insufficient).</li>
 * </ul>
 * </p>
 */
@WebMvcTest(
        controllers = AiTriggerSuggestionController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
        }
)
@Import(AiTriggerSuggestionControllerTest.MethodSecurityConfig.class)
@DisplayName("AiTriggerSuggestionController — ADMIN-gated endpoint")
class AiTriggerSuggestionControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @MockBean IncidentFeedbackAggregator feedbackAggregator;
    @MockBean TriggerSuggestionGenerator   suggestionGenerator;
    @MockBean @SuppressWarnings("unused") TenantRateLimiter tenantRateLimiter;

    private static final String URL = "/api/v1/ai/trigger-suggestions";

    // ─── Authorization ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @WithMockUser(username = "admin1", roles = "ADMIN")
        @DisplayName("CT-TSC-01: ADMIN → 200 OK")
        void admin_returns200() throws Exception {
            when(feedbackAggregator.collectRecentFeedback()).thenReturn(List.of());
            when(suggestionGenerator.generate(any())).thenReturn(List.of());

            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "operator1", roles = "OPERATOR")
        @DisplayName("CT-TSC-02: OPERATOR → 403 Forbidden")
        void operator_returns403() throws Exception {
            mockMvc.perform(get(URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "analyst1", roles = "ANALYST")
        @DisplayName("CT-TSC-03: ANALYST → 403 Forbidden")
        void analyst_returns403() throws Exception {
            mockMvc.perform(get(URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("CT-TSC-04: anonymous → 401 Unauthorized")
        void anonymous_returns401() throws Exception {
            mockMvc.perform(get(URL))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── Response payload ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Response payload")
    class ResponsePayload {

        @Test
        @WithMockUser(username = "admin1", roles = "ADMIN")
        @DisplayName("CT-TSC-05: insufficient feedback → 200 with empty array")
        void insufficientFeedback_emptyArray() throws Exception {
            when(feedbackAggregator.collectRecentFeedback()).thenReturn(List.of());
            when(suggestionGenerator.generate(any())).thenReturn(List.of());

            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @WithMockUser(username = "admin1", roles = "ADMIN")
        @DisplayName("CT-TSC-06: sufficient feedback → 200 with suggestion objects")
        void sufficientFeedback_returnsSuggestions() throws Exception {
            AlertEvent event = new AlertEvent();
            event.setModule("AQI");
            event.setMeasureType("PM25");
            event.setFeedbackCorrect(false);
            event.setDetectedAt(Instant.now());

            TriggerSuggestion suggestion = new TriggerSuggestion(
                    "AQI:PM25", 100.0, 120.0, 0.85,
                    "High false-positive rate over 200 records", Instant.now()
            );

            when(feedbackAggregator.collectRecentFeedback()).thenReturn(List.of(event));
            when(suggestionGenerator.generate(any())).thenReturn(List.of(suggestion));

            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].triggerType").value("AQI:PM25"))
                    .andExpect(jsonPath("$[0].currentThreshold").value(100.0))
                    .andExpect(jsonPath("$[0].suggestedThreshold").value(120.0))
                    .andExpect(jsonPath("$[0].confidence").value(0.85))
                    .andExpect(jsonPath("$[0].reason").isNotEmpty());
        }
    }
}
