package com.uip.backend.workflow.trigger.management;

import com.uip.backend.esg.dto.EsgAnomalyDto;
import com.uip.backend.esg.service.EsgService;
import com.uip.backend.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// DISABLED by S4-10 Config Engine. Replaced by GenericScheduledTriggerService + trigger_config rows: aiM03, aiM04.
// @Component
@RequiredArgsConstructor
@Slf4j
public class ManagementWorkflowScheduler {

    private final WorkflowService workflowService;
    private final EsgService      esgService;

    @Scheduled(fixedDelay = 120_000)
    public void checkUtilityAnomalies() {
        try {
            List<EsgAnomalyDto> anomalies = esgService.detectUtilityAnomalies();

            for (EsgAnomalyDto anomaly : anomalies) {
                boolean alreadyRunning = workflowService
                        .hasActiveProcess("aiM03_utilityIncidentCoordination",
                                "buildingId", anomaly.buildingId());
                if (alreadyRunning) continue;

                Map<String, Object> variables = Map.of(
                        "scenarioKey",  "aiM03_utilityIncidentCoordination",
                        "metricType",   anomaly.metricType(),
                        "anomalyValue", anomaly.currentValue(),
                        "buildingId",   anomaly.buildingId(),
                        "detectedAt",   Instant.now().toString()
                );
                workflowService.startProcess("aiM03_utilityIncidentCoordination", variables);
                log.info("AI-M03 started: building={}, metric={}, value={}",
                        anomaly.buildingId(), anomaly.metricType(), anomaly.currentValue());
            }
        } catch (Exception e) {
            log.error("AI-M03 scheduler failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 120_000)
    public void checkEsgAnomalies() {
        try {
            List<EsgAnomalyDto> anomalies = esgService.detectEsgAnomalies();

            for (EsgAnomalyDto anomaly : anomalies) {
                boolean alreadyRunning = workflowService
                        .hasActiveProcess("aiM04_esgAnomalyInvestigation",
                                "metricType", anomaly.metricType());
                if (alreadyRunning) continue;

                Map<String, Object> variables = Map.of(
                        "scenarioKey",   "aiM04_esgAnomalyInvestigation",
                        "metricType",    anomaly.metricType(),
                        "currentValue",  anomaly.currentValue(),
                        "historicalAvg", anomaly.historicalAvg(),
                        "period",        anomaly.period()
                );
                workflowService.startProcess("aiM04_esgAnomalyInvestigation", variables);
                log.info("AI-M04 started: metric={}, current={}, avg={}",
                        anomaly.metricType(), anomaly.currentValue(), anomaly.historicalAvg());
            }
        } catch (Exception e) {
            log.error("AI-M04 scheduler failed: {}", e.getMessage(), e);
        }
    }
}
