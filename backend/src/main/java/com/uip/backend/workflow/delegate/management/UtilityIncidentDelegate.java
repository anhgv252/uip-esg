package com.uip.backend.workflow.delegate.management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AI-M03: Utility Incident Coordination Delegate
 * Creates maintenance tickets for utility anomalies
 */
@Component("utilityIncidentDelegate")
@RequiredArgsConstructor
@Slf4j
public class UtilityIncidentDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String metricType = (String) execution.getVariable("metricType");
        Object anomalyValue = execution.getVariable("anomalyValue");
        String buildingId = (String) execution.getVariable("buildingId");
        String aiReasoning = (String) execution.getVariable("aiReasoning");
        String aiDecision = (String) execution.getVariable("aiDecision");

        log.info("Utility Incident for building {}: metric={}, value={}, decision={}", 
                buildingId, metricType, anomalyValue, aiDecision);

        // Generate maintenance ticket
        String maintenanceTicketId = UUID.randomUUID().toString();
        
        // Determine assigned team based on metric type
        String assignedTeam = determineTeam(metricType);
        
        // Generate diagnosis report
        String diagnosisReport = String.format(
                "Utility Incident Diagnosis:\n" +
                "- Building: %s\n" +
                "- Metric: %s\n" +
                "- Anomaly Value: %s\n" +
                "- AI Decision: %s\n" +
                "- Analysis: %s\n" +
                "- Assigned Team: %s",
                buildingId, metricType, anomalyValue, aiDecision, aiReasoning, assignedTeam
        );

        execution.setVariable("maintenanceTicketId", maintenanceTicketId);
        execution.setVariable("assignedTeam", assignedTeam);
        execution.setVariable("diagnosisReport", diagnosisReport);

        log.info("Maintenance ticket {} created and assigned to: {}", maintenanceTicketId, assignedTeam);
    }

    private String determineTeam(String metricType) {
        if (metricType == null) {
            return "GENERAL_MAINTENANCE";
        }
        
        return switch (metricType.toLowerCase()) {
            case "electricity", "power" -> "ELECTRICAL_TEAM";
            case "water", "water_flow" -> "PLUMBING_TEAM";
            case "gas" -> "GAS_TEAM";
            case "hvac", "temperature" -> "HVAC_TEAM";
            default -> "GENERAL_MAINTENANCE";
        };
    }
}
