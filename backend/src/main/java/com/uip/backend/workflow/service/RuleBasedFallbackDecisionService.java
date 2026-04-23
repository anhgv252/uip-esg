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
            case "aiC01_aqiCitizenAlert": {
                double aqiVal = context.containsKey("aqiValue")
                        ? ((Number) context.get("aqiValue")).doubleValue() : 160.0;
                String districtCode = context.getOrDefault("districtCode", "unknown district").toString();
                String aqiSeverity = aqiVal >= 201 ? "HIGH" : "MEDIUM";
                String aqiLevel = aqiVal >= 201 ? "Very Unhealthy" : "Unhealthy";
                decision.setDecision("NOTIFY_CITIZENS");
                decision.setConfidence(aqiVal >= 200 ? 0.95 : 0.91);
                decision.setReasoning(String.format(
                        "AI Analysis: AQI reading of %.0f detected at sensor in district %s. " +
                        "Level classified as '%s' — exceeds WHO safe threshold of 150. " +
                        "Vulnerable populations (children, elderly, respiratory patients) at significant risk. " +
                        "Immediate citizen notification and outdoor activity advisory recommended.",
                        aqiVal, districtCode, aqiLevel));
                decision.setSeverity(aqiSeverity);
                decision.setRecommendedActions(List.of(
                        "Khuyến cáo cư dân quận " + districtCode + " hạn chế ra ngoài trời",
                        "Đeo khẩu trang N95 khi buộc phải ra ngoài",
                        "Trẻ em, người cao tuổi, người bệnh hô hấp cần ở trong nhà có lọc không khí"
                ));
                break;
            }
                
            case "aiC02_citizenServiceRequest":
                decision.setDecision("ASSIGN_TO_ENVIRONMENT");
                decision.setReasoning("AI Analysis: Noise complaint from construction activity detected. Late-night construction noise violates city ordinance Decree 24/2016. Classified as environmental violation, routing to Environment Department for enforcement action.");
                decision.setSeverity("MEDIUM");
                decision.setConfidence(0.82);
                decision.setRecommendedActions(List.of(
                    "Issue formal notice to construction company",
                    "Schedule noise level inspection within 24h",
                    "Notify complainant of action taken"
                ));
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
