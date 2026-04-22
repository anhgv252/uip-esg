package com.uip.backend.workflow.delegate.citizen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * AI-C02: Citizen Service Request Delegate
 * Routes citizen service requests to appropriate department based on AI classification
 */
@Component("citizenServiceRequestDelegate")
@RequiredArgsConstructor
@Slf4j
public class CitizenServiceRequestDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String citizenId = (String) execution.getVariable("citizenId");
        String requestType = (String) execution.getVariable("requestType");
        String description = (String) execution.getVariable("description");
        String aiDecision = (String) execution.getVariable("aiDecision");
        String aiSeverity = (String) execution.getVariable("aiSeverity");
        @SuppressWarnings("unchecked")
        List<String> recommendedActions = (List<String>) execution.getVariable("aiRecommendedActions");

        log.info("Citizen Service Request from {}: type={}, decision={}", citizenId, requestType, aiDecision);

        // Determine department from AI decision
        String department = determineDepartment(aiDecision);

        // Determine priority from AI severity
        String priority = determinePriority(aiSeverity);

        // Get auto-response text from AI recommendations
        String autoResponseText = recommendedActions != null && !recommendedActions.isEmpty()
                ? recommendedActions.get(0)
                : "Your request has been received and will be reviewed shortly.";

        // Generate request ID
        String requestId = UUID.randomUUID().toString();

        execution.setVariable("department", department);
        execution.setVariable("autoResponseText", autoResponseText);
        execution.setVariable("requestId", requestId);
        execution.setVariable("priority", priority);

        log.info("Service request {} routed to department: {} with priority: {}", requestId, department, priority);
    }

    private String determineDepartment(String aiDecision) {
        if (aiDecision == null) {
            return "GENERAL";
        }

        return switch (aiDecision.toUpperCase()) {
            case "ASSIGN_TO_ENVIRONMENT" -> "ENVIRONMENT";
            case "ASSIGN_TO_UTILITIES" -> "UTILITIES";
            case "ASSIGN_TO_TRAFFIC" -> "TRAFFIC";
            case "ASSIGN_TO_GENERAL" -> "GENERAL";
            default -> "GENERAL";
        };
    }

    private String determinePriority(String aiSeverity) {
        if (aiSeverity == null) {
            return "MEDIUM";
        }
        return switch (aiSeverity.toUpperCase()) {
            case "CRITICAL" -> "HIGH";
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            case "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }
}
