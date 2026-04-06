package com.uip.backend.workflow.service;

import com.uip.backend.workflow.dto.AIDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Provides safe rule-based fallback decisions when Claude API is unavailable.
 */
@Service
@Slf4j
public class RuleBasedFallbackDecisionService {

    public AIDecision getFallbackDecision(String scenarioKey, Map<String, Object> context) {
        log.warn("Using fallback decision for scenario: {}", scenarioKey);
        
        AIDecision decision = new AIDecision();
        decision.setConfidence(0.5);
        
        switch (scenarioKey) {
            case "aiC01_aqiCitizenAlert":
                decision.setDecision("NOTIFY_CITIZENS");
                decision.setReasoning("Fallback: AQI threshold exceeded, notifying citizens as precaution");
                decision.setSeverity("MEDIUM");
                decision.setRecommendedActions(List.of("Send standard air quality alert", "Recommend staying indoors"));
                break;
                
            case "aiC02_citizenServiceRequest":
                decision.setDecision("ASSIGN_TO_GENERAL");
                decision.setReasoning("Fallback: Route to general department for manual triage");
                decision.setSeverity("LOW");
                decision.setRecommendedActions(List.of("Manual review required", "Assign to general services department"));
                break;
                
            case "aiC03_floodEmergencyEvacuation":
                decision.setDecision("ESCALATE_TO_HUMAN");
                decision.setReasoning("Fallback: Emergency situation requires human decision");
                decision.setSeverity("CRITICAL");
                decision.setRecommendedActions(List.of("Alert emergency services", "Activate emergency protocol"));
                break;
                
            case "aiM01_floodResponseCoordination":
                decision.setDecision("ESCALATE_TO_HUMAN");
                decision.setReasoning("Fallback: Flood response requires human coordination");
                decision.setSeverity("HIGH");
                decision.setRecommendedActions(List.of("Dispatch emergency team", "Notify city operations center"));
                break;
                
            case "aiM02_aqiTrafficControl":
                decision.setDecision("APPLY_STANDARD_RESTRICTIONS");
                decision.setReasoning("Fallback: Apply standard traffic restrictions for elevated AQI");
                decision.setSeverity("MEDIUM");
                decision.setRecommendedActions(List.of("Restrict heavy vehicle traffic", "Limit construction activities"));
                break;
                
            case "aiM03_utilityIncidentCoordination":
                decision.setDecision("CREATE_MAINTENANCE_TICKET");
                decision.setReasoning("Fallback: Create maintenance ticket for investigation");
                decision.setSeverity("MEDIUM");
                decision.setRecommendedActions(List.of("Assign to maintenance team", "Schedule inspection"));
                break;
                
            case "aiM04_esgAnomalyInvestigation":
                decision.setDecision("FLAG_FOR_REVIEW");
                decision.setReasoning("Fallback: Flag anomaly for manual review");
                decision.setSeverity("LOW");
                decision.setRecommendedActions(List.of("Add to review queue", "Compare with historical data"));
                break;
                
            default:
                decision.setDecision("ESCALATE_TO_HUMAN");
                decision.setReasoning("Fallback: Unknown scenario, requiring human review");
                decision.setSeverity("MEDIUM");
                decision.setRecommendedActions(List.of("Manual review required"));
        }
        
        return decision;
    }
}
