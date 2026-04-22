package com.uip.backend.workflow.delegate;

import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.service.ClaudeApiService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AIAnalysisDelegate")
class AIAnalysisDelegateTest {

    @Mock private ClaudeApiService claudeApiService;
    @Mock private DelegateExecution execution;

    @InjectMocks private AIAnalysisDelegate aiAnalysisDelegate;

    private static final String SCENARIO_KEY = "aiC01_aqiCitizenAlert";

    // ─── Case 1 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Happy path: AI decision được set vào tất cả process variables")
    void execute_happyPath_setsAllVariables() throws Exception {
        // Arrange
        AIDecision decision = buildDecision("NOTIFY_CITIZENS", "AQI is high", 0.92, List.of("Send alert", "Stay indoors"), "HIGH");
        when(execution.getVariable("scenarioKey")).thenReturn(SCENARIO_KEY);
        when(execution.getVariables()).thenReturn(new HashMap<>(Map.of("scenarioKey", SCENARIO_KEY, "aqiValue", "175")));
        when(execution.getProcessInstanceId()).thenReturn("pi-test-001");
        when(claudeApiService.analyzeAsync(eq(SCENARIO_KEY), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(decision));

        // Act
        aiAnalysisDelegate.execute(execution);

        // Assert
        verify(execution).setVariable("aiDecision", "NOTIFY_CITIZENS");
        verify(execution).setVariable("aiConfidence", 0.92);
        verify(execution).setVariable("aiSeverity", "HIGH");
    }

    // ─── Case 2 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("analyzeAsync throw exception → set error variables, không throw exception ra ngoài")
    void execute_analyzeAsyncThrows_setsErrorVariablesWithoutThrowing() throws Exception {
        // Arrange
        when(execution.getVariable("scenarioKey")).thenReturn(SCENARIO_KEY);
        when(execution.getVariables()).thenReturn(new HashMap<>(Map.of("scenarioKey", SCENARIO_KEY)));
        when(execution.getProcessInstanceId()).thenReturn("pi-test-001");
        when(claudeApiService.analyzeAsync(eq(SCENARIO_KEY), anyMap()))
                .thenThrow(new RuntimeException("API timeout"));

        // Act — không được throw exception
        aiAnalysisDelegate.execute(execution);

        // Assert
        verify(execution).setVariable("aiDecision", "ERROR");
    }

    // ─── Case 3 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Happy path: đủ 5 variables được set")
    void execute_happyPath_setsAllFiveVariables() throws Exception {
        // Arrange
        AIDecision decision = buildDecision("NOTIFY_CITIZENS", "AQI is high", 0.92, List.of("Send alert"), "HIGH");
        when(execution.getVariable("scenarioKey")).thenReturn(SCENARIO_KEY);
        when(execution.getVariables()).thenReturn(new HashMap<>(Map.of("scenarioKey", SCENARIO_KEY)));
        when(execution.getProcessInstanceId()).thenReturn("pi-test-001");
        when(claudeApiService.analyzeAsync(eq(SCENARIO_KEY), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(decision));

        // Act
        aiAnalysisDelegate.execute(execution);

        // Assert — verify đủ 5 variables
        verify(execution).setVariable("aiDecision", "NOTIFY_CITIZENS");
        verify(execution).setVariable("aiReasoning", "AQI is high");
        verify(execution).setVariable("aiConfidence", 0.92);
        verify(execution).setVariable(eq("aiRecommendedActions"), any());
        verify(execution).setVariable("aiSeverity", "HIGH");
    }

    // ─── Case 4 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("scenarioKey null → không throw exception ra ngoài execute()")
    void execute_nullScenarioKey_doesNotPropagateException() throws Exception {
        // Arrange
        when(execution.getVariable("scenarioKey")).thenReturn(null);
        when(execution.getVariables()).thenReturn(new HashMap<>());
        when(execution.getProcessInstanceId()).thenReturn("pi-test-001");
        // analyzeAsync(null, ...) → Mockito trả null CompletableFuture → NPE được catch trong delegate
        when(claudeApiService.analyzeAsync(isNull(), anyMap())).thenReturn(null);

        // Act — không được throw exception
        aiAnalysisDelegate.execute(execution);

        // Assert — error path được kích hoạt
        verify(execution).setVariable("aiDecision", "ERROR");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private AIDecision buildDecision(String decision, String reasoning, double confidence,
                                      List<String> actions, String severity) {
        AIDecision d = new AIDecision();
        d.setDecision(decision);
        d.setReasoning(reasoning);
        d.setConfidence(confidence);
        d.setRecommendedActions(actions);
        d.setSeverity(severity);
        return d;
    }
}
