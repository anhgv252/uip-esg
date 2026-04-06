package com.uip.backend.workflow.delegate.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI-M02: AQI Traffic & Construction Control Delegate
 * Recommends traffic restrictions based on air quality index
 */
@Component("aqiTrafficControlDelegate")
@RequiredArgsConstructor
@Slf4j
public class AqiTrafficControlDelegate implements JavaDelegate {

    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Object aqiValue = execution.getVariable("aqiValue");
        String pollutants = (String) execution.getVariable("pollutants");
        String affectedDistricts = (String) execution.getVariable("affectedDistricts");
        @SuppressWarnings("unchecked")
        List<String> recommendedActions = (List<String>) execution.getVariable("aiRecommendedActions");
        String aiSeverity = (String) execution.getVariable("aiSeverity");

        log.info("AQI Traffic Control: AQI={}, districts={}, severity={}", 
                aqiValue, affectedDistricts, aiSeverity);

        // Generate restriction areas from AI recommendations
        List<Map<String, String>> restrictionAreas = List.of(
                Map.of("district", affectedDistricts != null ? affectedDistricts : "Unknown",
                       "restriction", recommendedActions != null && !recommendedActions.isEmpty() 
                               ? recommendedActions.get(0) 
                               : "Monitor air quality")
        );

        String restrictionAreasJson;
        try {
            restrictionAreasJson = objectMapper.writeValueAsString(restrictionAreas);
        } catch (JsonProcessingException e) {
            restrictionAreasJson = "[]";
        }

        // Generate recommendation report
        String recommendationReport = String.format(
                "AQI Traffic Restriction Recommendation:\n" +
                "- AQI Level: %s\n" +
                "- Affected Districts: %s\n" +
                "- Severity: %s\n" +
                "- Recommended Actions: %s",
                aqiValue,
                affectedDistricts,
                aiSeverity,
                recommendedActions != null ? String.join(", ", recommendedActions) : "None"
        );

        execution.setVariable("restrictionAreas", restrictionAreasJson);
        execution.setVariable("recommendationReport", recommendationReport);

        log.info("Traffic restriction recommendation generated for districts: {}", affectedDistricts);
    }
}
