package com.uip.backend.workflow.service;

import com.uip.backend.workflow.dto.AIDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RuleBasedFallbackDecisionService")
class RuleBasedFallbackDecisionServiceTest {

    RuleBasedFallbackDecisionService service = new RuleBasedFallbackDecisionService();

    // ─── aiC01 AQI Citizen Alert ────────────────────────────────────────────

    @Nested
    @DisplayName("aiC01_aqiCitizenAlert")
    class AqiCitizenAlert {

        @Test
        @DisplayName("high AQI → HIGH severity, 0.95 confidence")
        void highAqi() {
            AIDecision d = service.getFallbackDecision("aiC01_aqiCitizenAlert",
                    Map.of("aqiValue", 250, "districtCode", "Q1"));

            assertThat(d.getDecision()).isEqualTo("NOTIFY_CITIZENS");
            assertThat(d.getSeverity()).isEqualTo("HIGH");
            assertThat(d.getConfidence()).isGreaterThanOrEqualTo(0.95);
            assertThat(d.getReasoning()).contains("Q1");
            assertThat(d.getRecommendedActions()).hasSize(3);
        }

        @Test
        @DisplayName("medium AQI → MEDIUM severity, 0.91 confidence")
        void mediumAqi() {
            AIDecision d = service.getFallbackDecision("aiC01_aqiCitizenAlert",
                    Map.of("aqiValue", 160, "districtCode", "Q7"));

            assertThat(d.getSeverity()).isEqualTo("MEDIUM");
            assertThat(d.getConfidence()).isBetween(0.90, 0.94);
            assertThat(d.getReasoning()).contains("Q7");
        }

        @Test
        @DisplayName("missing context → defaults (AQI 160, unknown district)")
        void missingContext() {
            AIDecision d = service.getFallbackDecision("aiC01_aqiCitizenAlert", Map.of());

            assertThat(d.getDecision()).isEqualTo("NOTIFY_CITIZENS");
            assertThat(d.getReasoning()).contains("unknown district");
        }

        @Test
        @DisplayName("boundary AQI 201 → HIGH severity")
        void boundary201() {
            AIDecision d = service.getFallbackDecision("aiC01_aqiCitizenAlert",
                    Map.of("aqiValue", 201));

            assertThat(d.getSeverity()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("boundary AQI 200 → uses >=200 confidence threshold")
        void boundary200() {
            AIDecision d = service.getFallbackDecision("aiC01_aqiCitizenAlert",
                    Map.of("aqiValue", 200));

            assertThat(d.getConfidence()).isGreaterThanOrEqualTo(0.95);
        }
    }

    // ─── aiC02 Citizen Service Request ───────────────────────────────────────

    @Test
    @DisplayName("aiC02 assigns to environment department")
    void aiC02() {
        AIDecision d = service.getFallbackDecision("aiC02_citizenServiceRequest", Map.of());

        assertThat(d.getDecision()).isEqualTo("ASSIGN_TO_ENVIRONMENT");
        assertThat(d.getSeverity()).isEqualTo("MEDIUM");
        assertThat(d.getConfidence()).isEqualTo(0.82);
        assertThat(d.getRecommendedActions()).hasSize(3);
    }

    // ─── aiC03 Flood Emergency ───────────────────────────────────────────────

    @Test
    @DisplayName("aiC03 escalates to human with CRITICAL severity")
    void aiC03() {
        AIDecision d = service.getFallbackDecision("aiC03_floodEmergencyEvacuation", Map.of());

        assertThat(d.getDecision()).isEqualTo("ESCALATE_TO_HUMAN");
        assertThat(d.getSeverity()).isEqualTo("CRITICAL");
        assertThat(d.getRecommendedActions()).hasSizeGreaterThanOrEqualTo(1);
    }

    // ─── aiM01 Flood Response ────────────────────────────────────────────────

    @Test
    @DisplayName("aiM01 escalates to human with HIGH severity")
    void aiM01() {
        AIDecision d = service.getFallbackDecision("aiM01_floodResponseCoordination", Map.of());

        assertThat(d.getDecision()).isEqualTo("ESCALATE_TO_HUMAN");
        assertThat(d.getSeverity()).isEqualTo("HIGH");
    }

    // ─── aiM02 AQI Traffic Control ───────────────────────────────────────────

    @Test
    @DisplayName("aiM02 applies standard restrictions")
    void aiM02() {
        AIDecision d = service.getFallbackDecision("aiM02_aqiTrafficControl", Map.of());

        assertThat(d.getDecision()).isEqualTo("APPLY_STANDARD_RESTRICTIONS");
        assertThat(d.getSeverity()).isEqualTo("MEDIUM");
    }

    // ─── aiM03 Utility Incident ──────────────────────────────────────────────

    @Test
    @DisplayName("aiM03 creates maintenance ticket")
    void aiM03() {
        AIDecision d = service.getFallbackDecision("aiM03_utilityIncidentCoordination", Map.of());

        assertThat(d.getDecision()).isEqualTo("CREATE_MAINTENANCE_TICKET");
    }

    // ─── aiM04 ESG Anomaly ───────────────────────────────────────────────────

    @Test
    @DisplayName("aiM04 flags for review with LOW severity")
    void aiM04() {
        AIDecision d = service.getFallbackDecision("aiM04_esgAnomalyInvestigation", Map.of());

        assertThat(d.getDecision()).isEqualTo("FLAG_FOR_REVIEW");
        assertThat(d.getSeverity()).isEqualTo("LOW");
    }

    // ─── default / unknown ───────────────────────────────────────────────────

    @Test
    @DisplayName("unknown scenario → ESCALATE_TO_HUMAN")
    void unknownScenario() {
        AIDecision d = service.getFallbackDecision("unknown_scenario_xyz", Map.of());

        assertThat(d.getDecision()).isEqualTo("ESCALATE_TO_HUMAN");
        assertThat(d.getSeverity()).isEqualTo("MEDIUM");
        assertThat(d.getRecommendedActions()).containsExactly("Manual review required");
    }

    // ─── AIDecision field sanity ─────────────────────────────────────────────

    @ParameterizedTest(name = "scenario {0} sets all AIDecision fields")
    @ValueSource(strings = {
            "aiC01_aqiCitizenAlert",
            "aiC02_citizenServiceRequest",
            "aiC03_floodEmergencyEvacuation",
            "aiM01_floodResponseCoordination",
            "aiM02_aqiTrafficControl",
            "aiM03_utilityIncidentCoordination",
            "aiM04_esgAnomalyInvestigation",
            "totally_unknown"
    })
    @DisplayName("every scenario returns fully populated AIDecision")
    void allFieldsPopulated(String scenario) {
        Map<String, Object> ctx = scenario.equals("aiC01_aqiCitizenAlert")
                ? Map.of("aqiValue", 180, "districtCode", "Q1")
                : Map.of();

        AIDecision d = service.getFallbackDecision(scenario, ctx);

        assertThat(d.getDecision()).isNotBlank();
        assertThat(d.getReasoning()).isNotBlank();
        assertThat(d.getSeverity()).isNotBlank();
        assertThat(d.getConfidence()).isPositive();
        assertThat(d.getRecommendedActions()).isNotEmpty();
    }
}
