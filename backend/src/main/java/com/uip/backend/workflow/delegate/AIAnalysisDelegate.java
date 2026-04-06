package com.uip.backend.workflow.delegate;

import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.service.ClaudeApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Generic AI Analysis Delegate for Camunda workflows.
 * Reads scenarioKey from execution variables, calls Claude API, and sets result variables.
 */
@Component("aiAnalysisDelegate")
@RequiredArgsConstructor
@Slf4j
public class AIAnalysisDelegate implements JavaDelegate {

    private final ClaudeApiService claudeApiService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String scenarioKey = (String) execution.getVariable("scenarioKey");
        log.info("AI Analysis for scenario: {} in process instance: {}", scenarioKey, execution.getProcessInstanceId());

        // Build context map from execution variables
        Map<String, Object> context = new HashMap<>(execution.getVariables());

        try {
            // Call Claude API with 10s timeout
            AIDecision decision = claudeApiService.analyzeAsync(scenarioKey, context)
                    .get(10, TimeUnit.SECONDS);

            // Set result variables
            execution.setVariable("aiDecision", decision.getDecision());
            execution.setVariable("aiReasoning", decision.getReasoning());
            execution.setVariable("aiConfidence", decision.getConfidence());
            execution.setVariable("aiRecommendedActions", decision.getRecommendedActions());
            execution.setVariable("aiSeverity", decision.getSeverity());

            log.info("AI decision for {}: {} (confidence: {})", scenarioKey, decision.getDecision(), decision.getConfidence());

        } catch (Exception e) {
            log.error("AI analysis failed for scenario {}: {}", scenarioKey, e.getMessage());
            // Set error variables but don't throw - let process continue with fallback
            execution.setVariable("aiDecision", "ERROR");
            execution.setVariable("aiReasoning", "AI analysis failed: " + e.getMessage());
            execution.setVariable("aiConfidence", 0.0);
            execution.setVariable("aiSeverity", "UNKNOWN");
        }
    }
}
