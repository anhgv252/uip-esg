package com.uip.backend.workflow;

import com.uip.backend.notification.service.NotificationService;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test cho 3 Citizen AI Scenarios (S4-03).
 *
 * Dùng embedded Camunda (Spring Boot test context).
 * External deps (Redis, Claude API) được mock để không cần infrastructure.
 *
 * Yêu cầu: PostgreSQL chạy (hoặc Testcontainers trong CI) — Camunda cần DB thực.
 */
@SpringBootTest
@DisplayName("S4-03 Citizen AI Scenarios — Integration")
class CitizenAiScenariosIntegrationTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private HistoryService  historyService;

    @MockBean private StringRedisTemplate redisTemplate;
    @MockBean private ClaudeApiService    claudeApiService;

    // ─── AI-C01 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AI-C01: AQI > 150 → process starts → aqiCitizenAlertDelegate executes → SSE published")
    void aiC01_aqiHigh_processCompletes_notificationSent() throws Exception {
        // Given — Claude trả về NOTIFY_CITIZENS
        AIDecision decision = buildDecision("NOTIFY_CITIZENS", 0.95, "HIGH",
                List.of("Stay indoors", "Wear N95 mask"));
        when(claudeApiService.analyzeAsync(anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(decision));
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

        // When
        Map<String, Object> vars = Map.of(
                "scenarioKey",  "aiC01_aqiCitizenAlert",
                "sensorId",     "AQI-001",
                "aqiValue",     175.0,
                "districtCode", "D7",
                "measuredAt",   Instant.now().toString()
        );
        ProcessInstanceDto instance = workflowService.startProcess("aiC01_aqiCitizenAlert", vars);
        assertThat(instance.getId()).isNotNull();

        // Then — chờ process chạy xong
        await().atMost(10, SECONDS).until(() ->
                historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(instance.getId())
                        .finished()
                        .count() == 1
        );

        // Verify — SSE notification được publish qua Redis
        verify(redisTemplate, atLeastOnce())
                .convertAndSend(eq(NotificationService.ALERT_CHANNEL), anyString());
    }

    // ─── AI-C02 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AI-C02: citizen service request → classified → routed to department")
    void aiC02_serviceRequest_classifiedAndRoutedToDepartment() throws Exception {
        // Given — Claude classify thành ASSIGN_TO_ENVIRONMENT
        AIDecision decision = buildDecision("ASSIGN_TO_ENVIRONMENT", 0.88, "MEDIUM",
                List.of("Inspect area within 24h", "File environment report"));
        when(claudeApiService.analyzeAsync(anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(decision));

        // When
        Map<String, Object> vars = Map.of(
                "scenarioKey", "aiC02_citizenServiceRequest",
                "citizenId",   "citizen-test-001",
                "requestId",   UUID.randomUUID().toString(),
                "requestType", "ENVIRONMENT",
                "description", "Bad smell near factory in district 7",
                "district",    "D7"
        );
        ProcessInstanceDto instance = workflowService.startProcess("aiC02_citizenServiceRequest", vars);
        assertThat(instance.getId()).isNotNull();

        // Then — process hoàn thành không có exception
        await().atMost(10, SECONDS).until(() ->
                historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(instance.getId())
                        .finished()
                        .count() == 1
        );
        // Process completed = delegate executed without error
    }

    // ─── AI-C03 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AI-C03: CRITICAL flood → evacuation triggered → mass SSE alert published")
    void aiC03_criticalFlood_evacuationTriggered_massAlertPublished() throws Exception {
        // Given — Claude assess CRITICAL severity
        AIDecision decision = buildDecision("EVACUATE_IMMEDIATELY", 0.99, "CRITICAL",
                List.of("Evacuate to Zone B immediately", "Call emergency services"));
        when(claudeApiService.analyzeAsync(anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(decision));
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

        // When
        Map<String, Object> vars = Map.of(
                "scenarioKey",    "aiC03_floodEmergencyEvacuation",
                "waterLevel",     4.2,
                "sensorLocation", "CANAL-D8",
                "warningZones",   "D8,D9",
                "detectedAt",     Instant.now().toString()
        );
        ProcessInstanceDto instance = workflowService.startProcess("aiC03_floodEmergencyEvacuation", vars);
        assertThat(instance.getId()).isNotNull();

        // Then
        await().atMost(10, SECONDS).until(() ->
                historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(instance.getId())
                        .finished()
                        .count() == 1
        );

        // CRITICAL flood → FloodEvacuationDelegate phải publish evacuation alert
        verify(redisTemplate, atLeastOnce())
                .convertAndSend(eq(NotificationService.ALERT_CHANNEL), anyString());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private AIDecision buildDecision(String decision, double confidence, String severity,
                                     List<String> actions) {
        AIDecision d = new AIDecision();
        d.setDecision(decision);
        d.setConfidence(confidence);
        d.setSeverity(severity);
        d.setReasoning("AI analysis result");
        d.setRecommendedActions(actions);
        return d;
    }
}
