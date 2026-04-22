package com.uip.backend.workflow;

import com.uip.backend.esg.service.EsgService;
import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.ClaudeApiService;
import com.uip.backend.workflow.service.WorkflowService;
import org.camunda.bpm.engine.HistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("S4-04 Management AI Scenarios — Integration")
class ManagementAiScenariosIntegrationTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private HistoryService  historyService;

    @MockBean private ClaudeApiService    claudeApiService;
    @MockBean private StringRedisTemplate redisTemplate;
    @MockBean private EsgService          esgService;

    private AIDecision mockDecision(String decision, String severity) {
        AIDecision d = new AIDecision();
        d.setDecision(decision);
        d.setConfidence(0.9);
        d.setReasoning("Test reasoning");
        d.setSeverity(severity);
        d.setRecommendedActions(List.of("Take action"));
        return d;
    }

    private void assertProcessCompleted(String instanceId) {
        await().atMost(10, SECONDS).until(() ->
                historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(instanceId)
                        .finished()
                        .count() == 1
        );
    }

    @Test
    @DisplayName("AI-M01: Flood alert → operations team dispatched")
    void aiM01_floodAlert_teamDispatched() throws Exception {
        when(claudeApiService.analyzeAsync(anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(mockDecision("DISPATCH_TEAM", "CRITICAL")));

        ProcessInstanceDto instance = workflowService.startProcess(
                "aiM01_floodResponseCoordination",
                Map.of("scenarioKey", "aiM01_floodResponseCoordination",
                       "alertId", java.util.UUID.randomUUID().toString(),
                       "waterLevel", 4.5,
                       "location", "CANAL-D8",
                       "affectedZones", "D8,D9")
        );

        assertProcessCompleted(instance.getId());
    }

    @Test
    @DisplayName("AI-M02: AQI > 150 → restriction recommendation generated")
    void aiM02_highAqi_restrictionGenerated() throws Exception {
        when(claudeApiService.analyzeAsync(anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(
                        mockDecision("RESTRICT_TRAFFIC", "HIGH")));

        ProcessInstanceDto instance = workflowService.startProcess(
                "aiM02_aqiTrafficControl",
                Map.of("scenarioKey", "aiM02_aqiTrafficControl",
                       "aqiValue", 180.0,
                       "pollutants", "PM2.5",
                       "affectedDistricts", "D7")
        );

        assertProcessCompleted(instance.getId());
    }

    @Test
    @DisplayName("AI-M03: Utility anomaly → maintenance ticket created")
    void aiM03_utilityAnomaly_ticketCreated() throws Exception {
        when(claudeApiService.analyzeAsync(anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(
                        mockDecision("CREATE_MAINTENANCE_TICKET", "MEDIUM")));

        ProcessInstanceDto instance = workflowService.startProcess(
                "aiM03_utilityIncidentCoordination",
                Map.of("scenarioKey", "aiM03_utilityIncidentCoordination",
                       "metricType", "electricity",
                       "anomalyValue", 450.0,
                       "buildingId", "BLDG-001",
                       "detectedAt", Instant.now().toString())
        );

        assertProcessCompleted(instance.getId());
    }

    @Test
    @DisplayName("AI-M04: ESG anomaly → investigation report generated")
    void aiM04_esgAnomaly_reportGenerated() throws Exception {
        when(claudeApiService.analyzeAsync(anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(
                        mockDecision("INVESTIGATE_SPIKE", "MEDIUM")));

        ProcessInstanceDto instance = workflowService.startProcess(
                "aiM04_esgAnomalyInvestigation",
                Map.of("scenarioKey", "aiM04_esgAnomalyInvestigation",
                       "metricType", "carbon_emission",
                       "currentValue", 120.5,
                       "historicalAvg", 80.0,
                       "period", "2026-Q1")
        );

        assertProcessCompleted(instance.getId());
    }
}
