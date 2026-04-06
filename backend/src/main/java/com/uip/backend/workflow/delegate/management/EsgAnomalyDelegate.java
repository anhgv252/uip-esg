package com.uip.backend.workflow.delegate.management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AI-M04: ESG Anomaly Investigation Delegate
 * Investigates ESG metric anomalies and generates investigation reports
 */
@Component("esgAnomalyDelegate")
@RequiredArgsConstructor
@Slf4j
public class EsgAnomalyDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String metricType = (String) execution.getVariable("metricType");
        Object currentValue = execution.getVariable("currentValue");
        Object historicalAvg = execution.getVariable("historicalAvg");
        String period = (String) execution.getVariable("period");
        String aiReasoning = (String) execution.getVariable("aiReasoning");
        String aiDecision = (String) execution.getVariable("aiDecision");

        log.info("ESG Anomaly Investigation: metric={}, current={}, avg={}, decision={}", 
                metricType, currentValue, historicalAvg, aiDecision);

        // Generate investigation report ID
        String investigationReportId = UUID.randomUUID().toString();
        
        // Categorize anomaly
        String anomalyCategory = categorizeAnomaly(metricType, aiDecision);
        
        // Generate investigation summary
        String investigationSummary = String.format(
                "ESG Anomaly Investigation Report [%s]\n\n" +
                "Metric Type: %s\n" +
                "Current Value: %s\n" +
                "Historical Average: %s\n" +
                "Period: %s\n" +
                "Anomaly Category: %s\n\n" +
                "AI Analysis:\n" +
                "- Decision: %s\n" +
                "- Reasoning: %s\n\n" +
                "Recommended Actions:\n" +
                "- Compare with peer buildings in same district\n" +
                "- Review operational changes during period\n" +
                "- Schedule follow-up review in 7 days",
                investigationReportId,
                metricType,
                currentValue,
                historicalAvg,
                period,
                anomalyCategory,
                aiDecision,
                aiReasoning
        );

        execution.setVariable("investigationReportId", investigationReportId);
        execution.setVariable("anomalyCategory", anomalyCategory);
        execution.setVariable("investigationSummary", investigationSummary);

        log.info("ESG investigation report {} generated for metric: {}", investigationReportId, metricType);
    }

    private String categorizeAnomaly(String metricType, String aiDecision) {
        if (aiDecision != null && aiDecision.contains("SPIKE")) {
            return "SUDDEN_SPIKE";
        } else if (aiDecision != null && aiDecision.contains("DROP")) {
            return "SUDDEN_DROP";
        } else if (aiDecision != null && aiDecision.contains("TREND")) {
            return "GRADUAL_TREND";
        } else if (metricType != null) {
            if (metricType.toLowerCase().contains("energy")) {
                return "ENERGY_ANOMALY";
            } else if (metricType.toLowerCase().contains("water")) {
                return "WATER_ANOMALY";
            } else if (metricType.toLowerCase().contains("carbon")) {
                return "CARBON_ANOMALY";
            }
        }
        return "GENERAL_ANOMALY";
    }
}
