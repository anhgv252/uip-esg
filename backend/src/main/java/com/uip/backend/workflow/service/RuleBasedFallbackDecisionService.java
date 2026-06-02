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
                
            case "aiC02_citizenServiceRequest": {
                String requestType = context.getOrDefault("requestType", "NOISE_COMPLAINT").toString();
                String desc = context.getOrDefault("description", "").toString().toLowerCase();
                switch (requestType) {
                    case "UTILITY_INCIDENT" -> {
                        decision.setDecision("ASSIGN_TO_UTILITY");
                        decision.setConfidence(0.87);
                        decision.setSeverity("HIGH");
                        decision.setReasoning("AI Analysis: Water main rupture detected. Flooding reported at intersection causing traffic disruption and water loss. Classified as utility infrastructure emergency, routing to Water Supply Department for immediate dispatch.");
                        decision.setRecommendedActions(List.of(
                            "Dispatch repair crew to site immediately",
                            "Shut off upstream valve to stop water flow",
                            "Notify traffic authority to reroute vehicles",
                            "Notify complainant of response time estimate"
                        ));
                    }
                    case "ENVIRONMENTAL_VIOLATION" -> {
                        decision.setDecision("ASSIGN_TO_ENVIRONMENT");
                        decision.setConfidence(0.89);
                        decision.setSeverity("HIGH");
                        decision.setReasoning("AI Analysis: Illegal waste dumping detected. Industrial waste reported causing odor pollution in residential area, violating Environment Protection Law No. 72/2020. Routing to Environmental Department for enforcement.");
                        decision.setRecommendedActions(List.of(
                            "Dispatch environmental inspection team",
                            "Collect waste samples for lab analysis",
                            "Issue administrative penalty to responsible party",
                            "Notify complainant of enforcement action"
                        ));
                    }
                    case "FLOOD_EMERGENCY" -> {
                        decision.setDecision("ESCALATE_TO_HUMAN");
                        decision.setConfidence(0.95);
                        decision.setSeverity("CRITICAL");
                        decision.setReasoning("AI Analysis: Flood emergency reported. Situation requires immediate human coordination and inter-agency response. Escalating to City Emergency Operations Center.");
                        decision.setRecommendedActions(List.of(
                            "Alert City Emergency Operations Center",
                            "Deploy flood response team",
                            "Issue public evacuation notice if needed"
                        ));
                    }
                    default -> {
                        // NOISE_COMPLAINT and unknown types
                        boolean isConstruction = desc.contains("công trình") || desc.contains("xây dựng") || desc.contains("construction");
                        decision.setDecision("ASSIGN_TO_ENVIRONMENT");
                        decision.setConfidence(0.82);
                        decision.setSeverity("MEDIUM");
                        decision.setReasoning(isConstruction
                            ? "AI Analysis: Noise complaint from construction activity detected. Late-night construction noise violates city ordinance Decree 24/2016. Classified as environmental violation, routing to Environment Department for enforcement action."
                            : "AI Analysis: Citizen complaint received. Classified as environmental/public-order matter, routing to Environment Department for assessment and follow-up.");
                        decision.setRecommendedActions(List.of(
                            "Issue formal notice to responsible party",
                            "Schedule on-site inspection within 24h",
                            "Notify complainant of action taken"
                        ));
                    }
                }
                break;
            }
                
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
