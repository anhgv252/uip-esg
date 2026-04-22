package com.uip.backend.workflow.delegate.citizen;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CitizenServiceRequestDelegate")
class CitizenServiceRequestDelegateTest {

    @Mock private DelegateExecution execution;
    @InjectMocks private CitizenServiceRequestDelegate delegate;

    // ─── Case 1 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ASSIGN_TO_ENVIRONMENT → department = ENVIRONMENT")
    void execute_assignToEnvironment_setsCorrectDepartment() throws Exception {
        setupExecution("citizen-001", "ENVIRONMENT", "Bad smell", "ASSIGN_TO_ENVIRONMENT", null);

        delegate.execute(execution);

        verify(execution).setVariable("department", "ENVIRONMENT");
    }

    // ─── Case 2 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ASSIGN_TO_UTILITIES → department = UTILITIES")
    void execute_assignToUtilities_setsCorrectDepartment() throws Exception {
        setupExecution("citizen-002", "WATER", "No water", "ASSIGN_TO_UTILITIES", null);

        delegate.execute(execution);

        verify(execution).setVariable("department", "UTILITIES");
    }

    // ─── Case 3 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiDecision không xác định → department = GENERAL")
    void execute_unknownDecision_defaultsToGeneral() throws Exception {
        setupExecution("citizen-003", "OTHER", "Random issue", "SOME_UNKNOWN_VALUE", null);

        delegate.execute(execution);

        verify(execution).setVariable("department", "GENERAL");
    }

    // ─── Case 4 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("requestId được set ở dạng UUID hợp lệ (36 chars)")
    void execute_setsValidUuidRequestId() throws Exception {
        setupExecution("citizen-001", "ROAD", "Pothole", "ASSIGN_TO_GENERAL", null);

        delegate.execute(execution);

        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("requestId"), requestIdCaptor.capture());
        assertThat(requestIdCaptor.getValue()).hasSize(36);
    }

    // ─── Case 5 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiRecommendedActions không null → autoResponseText = first element")
    void execute_withRecommendations_usesFirstAsAutoResponse() throws Exception {
        List<String> actions = List.of("Please wait 2 business days.", "Check portal for status.");
        setupExecution("citizen-004", "TRAFFIC", "Broken light", "ASSIGN_TO_TRAFFIC", actions);

        delegate.execute(execution);

        ArgumentCaptor<String> autoResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("autoResponseText"), autoResponseCaptor.capture());
        assertThat(autoResponseCaptor.getValue()).isEqualTo("Please wait 2 business days.");
    }

    // ─── Case 6 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiRecommendedActions = null → autoResponseText là default message")
    void execute_nullRecommendations_usesDefaultAutoResponse() throws Exception {
        setupExecution("citizen-005", "GENERAL", "Complaint", "ASSIGN_TO_GENERAL", null);

        delegate.execute(execution);

        ArgumentCaptor<String> autoResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("autoResponseText"), autoResponseCaptor.capture());
        assertThat(autoResponseCaptor.getValue()).containsIgnoringCase("received");
    }

    // ─── Case 7 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiDecision = null → không throw exception, department = GENERAL")
    void execute_nullDecision_doesNotThrow() {
        setupExecution("citizen-006", "OTHER", "Unknown", null, null);

        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
        verify(execution).setVariable("department", "GENERAL");
    }

    // ─── Case 8 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiSeverity = CRITICAL → priority = HIGH")
    void execute_criticalSeverity_setsHighPriority() throws Exception {
        setupExecution("citizen-007", "ENVIRONMENT", "Urgent issue", "ASSIGN_TO_ENVIRONMENT", "CRITICAL", null);

        delegate.execute(execution);

        verify(execution).setVariable("priority", "HIGH");
    }

    // ─── Case 9 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiSeverity = LOW → priority = LOW")
    void execute_lowSeverity_setsLowPriority() throws Exception {
        setupExecution("citizen-008", "ROAD", "Minor issue", "ASSIGN_TO_GENERAL", "LOW", null);

        delegate.execute(execution);

        verify(execution).setVariable("priority", "LOW");
    }

    // ─── Case 10 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiSeverity = null → priority = MEDIUM (default)")
    void execute_nullSeverity_defaultsToMedium() throws Exception {
        setupExecution("citizen-009", "OTHER", "Some issue", "ASSIGN_TO_GENERAL", null);

        delegate.execute(execution);

        verify(execution).setVariable("priority", "MEDIUM");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void setupExecution(String citizenId, String requestType, String description,
                                String aiDecision, List<String> recommendedActions) {
        setupExecution(citizenId, requestType, description, aiDecision, null, recommendedActions);
    }

    private void setupExecution(String citizenId, String requestType, String description,
                                String aiDecision, String aiSeverity, List<String> recommendedActions) {
        when(execution.getVariable("citizenId")).thenReturn(citizenId);
        when(execution.getVariable("requestType")).thenReturn(requestType);
        when(execution.getVariable("description")).thenReturn(description);
        when(execution.getVariable("aiDecision")).thenReturn(aiDecision);
        when(execution.getVariable("aiSeverity")).thenReturn(aiSeverity);
        when(execution.getVariable("aiRecommendedActions")).thenReturn(recommendedActions);
    }
}
