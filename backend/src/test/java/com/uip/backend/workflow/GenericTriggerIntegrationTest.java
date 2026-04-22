package com.uip.backend.workflow;

import com.uip.backend.esg.dto.EsgAnomalyDto;
import com.uip.backend.esg.service.EsgService;
import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.service.ClaudeApiService;
import com.uip.backend.workflow.service.WorkflowService;
import com.uip.backend.workflow.trigger.GenericKafkaTriggerService;
import com.uip.backend.workflow.trigger.GenericScheduledTriggerService;
import org.camunda.bpm.engine.HistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("S4-10 Generic Trigger Integration — 7 AI Scenarios")
class GenericTriggerIntegrationTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private HistoryService historyService;
    @Autowired private GenericKafkaTriggerService kafkaTriggerService;
    @Autowired private GenericScheduledTriggerService scheduledTriggerService;
    @Autowired private TriggerConfigRepository configRepo;

    @MockBean private ClaudeApiService claudeApiService;
    @MockBean private StringRedisTemplate redisTemplate;
    @MockBean private EsgService esgService;

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

    private Acknowledgment mockAck() {
        Acknowledgment ack = mock(Acknowledgment.class);
        return ack;
    }

    @BeforeEach
    void stubClaude() {
        // Default: fallback decision for all scenarios
        when(claudeApiService.analyzeAsync(anyString(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(
                        mockDecision("NOTIFY_CITIZENS", "HIGH")));
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);
    }

    // ─── Kafka-triggered scenarios (C01, C03, M01, M02) ────────────────────

    @Nested
    @DisplayName("Kafka Generic Trigger")
    class KafkaTriggerTests {

        @Test
        @DisplayName("AI-C01: AQI event → GenericKafkaTriggerService → process completes")
        void aiC01_aqiEvent_processCompletes() {
            // Setup specific Claude response
            when(claudeApiService.analyzeAsync(eq("aiC01_aqiCitizenAlert"), anyMap()))
                    .thenReturn(CompletableFuture.completedFuture(
                            mockDecision("NOTIFY_CITIZENS", "HIGH")));

            // Build Kafka payload matching filter conditions
            Map<String, Object> payload = Map.of(
                    "module", "ENVIRONMENT",
                    "measureType", "AQI",
                    "value", 175.0,
                    "sensorId", "AQI-IT-001",
                    "districtCode", "D7",
                    "detectedAt", Instant.now().toString()
            );

            Acknowledgment ack = mockAck();

            // Act — trigger via generic Kafka path
            kafkaTriggerService.onKafkaEvent(payload, ack);

            // Assert — verify a process was started and completed
            await().atMost(10, SECONDS).until(() ->
                    historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey("aiC01_aqiCitizenAlert")
                            .finished()
                            .count() >= 1
            );

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("AI-C03: Flood WATER_LEVEL event → process completes")
        void aiC03_floodEvent_processCompletes() {
            when(claudeApiService.analyzeAsync(eq("aiC03_floodEmergencyEvacuation"), anyMap()))
                    .thenReturn(CompletableFuture.completedFuture(
                            mockDecision("EVACUATE_IMMEDIATELY", "CRITICAL")));

            Map<String, Object> payload = Map.of(
                    "module", "FLOOD",
                    "measureType", "WATER_LEVEL",
                    "value", 4.2,
                    "sensorId", "CANAL-D8",
                    "districtCode", "D8,D9",
                    "detectedAt", Instant.now().toString()
            );

            Acknowledgment ack = mockAck();
            kafkaTriggerService.onKafkaEvent(payload, ack);

            await().atMost(10, SECONDS).until(() ->
                    historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey("aiC03_floodEmergencyEvacuation")
                            .finished()
                            .count() >= 1
            );
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("AI-M01: Flood response → process completes")
        void aiM01_floodResponse_processCompletes() {
            when(claudeApiService.analyzeAsync(eq("aiM01_floodResponseCoordination"), anyMap()))
                    .thenReturn(CompletableFuture.completedFuture(
                            mockDecision("DISPATCH_TEAM", "CRITICAL")));

            Map<String, Object> payload = Map.of(
                    "measureType", "WATER_LEVEL",
                    "value", 4.5,
                    "sensorId", "CANAL-D8",
                    "districtCode", "D8",
                    "alertId", java.util.UUID.randomUUID().toString()
            );

            Acknowledgment ack = mockAck();
            kafkaTriggerService.onKafkaEvent(payload, ack);

            await().atMost(10, SECONDS).until(() ->
                    historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey("aiM01_floodResponseCoordination")
                            .finished()
                            .count() >= 1
            );
        }

        @Test
        @DisplayName("AI-M02: AQI > 150 traffic control → process completes")
        void aiM02_aqiTrafficControl_processCompletes() {
            when(claudeApiService.analyzeAsync(eq("aiM02_aqiTrafficControl"), anyMap()))
                    .thenReturn(CompletableFuture.completedFuture(
                            mockDecision("RESTRICT_TRAFFIC", "HIGH")));

            Map<String, Object> payload = Map.of(
                    "module", "ENVIRONMENT",
                    "measureType", "AQI",
                    "value", 200.0,
                    "sensorId", "AQI-D1-001",
                    "districtCode", "D1"
            );

            Acknowledgment ack = mockAck();
            kafkaTriggerService.onKafkaEvent(payload, ack);

            await().atMost(10, SECONDS).until(() ->
                    historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey("aiM02_aqiTrafficControl")
                            .finished()
                            .count() >= 1
            );
        }

        @Test
        @DisplayName("Filter mismatch → no process started")
        void filterMismatch_noProcessStarted() {
            // AQI = 50, which is below threshold 150
            Map<String, Object> payload = Map.of(
                    "module", "ENVIRONMENT",
                    "measureType", "AQI",
                    "value", 50.0,
                    "sensorId", "AQI-LOW",
                    "districtCode", "D1"
            );

            long before = historyService.createHistoricProcessInstanceQuery()
                    .processDefinitionKey("aiC01_aqiCitizenAlert")
                    .count();

            Acknowledgment ack = mockAck();
            kafkaTriggerService.onKafkaEvent(payload, ack);

            // No new C01 process should be started
            assertThat(historyService.createHistoricProcessInstanceQuery()
                    .processDefinitionKey("aiC01_aqiCitizenAlert")
                    .count()).isEqualTo(before);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("One Kafka event triggers multiple matching scenarios (C01 + M02)")
        void oneEvent_multipleMatches() {
            when(claudeApiService.analyzeAsync(anyString(), anyMap()))
                    .thenReturn(CompletableFuture.completedFuture(
                            mockDecision("NOTIFY_CITIZENS", "HIGH")));

            Map<String, Object> payload = Map.of(
                    "module", "ENVIRONMENT",
                    "measureType", "AQI",
                    "value", 200.0,
                    "sensorId", "AQI-BOTH",
                    "districtCode", "D7"
            );

            Acknowledgment ack = mockAck();
            kafkaTriggerService.onKafkaEvent(payload, ack);

            // Both C01 and M02 should be triggered (same filter conditions)
            await().atMost(10, SECONDS).until(() ->
                    historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey("aiC01_aqiCitizenAlert")
                            .finished()
                            .count() >= 1
            );
            await().atMost(10, SECONDS).until(() ->
                    historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey("aiM02_aqiTrafficControl")
                            .finished()
                            .count() >= 1
            );
        }
    }

    // ─── Scheduled-triggered scenarios (M03, M04) ─────────────────────────

    @Nested
    @DisplayName("Scheduled Generic Trigger")
    class ScheduledTriggerTests {

        @Test
        @DisplayName("AI-M03: Utility anomaly detected → process completes")
        void aiM03_utilityAnomaly_processCompletes() {
            when(claudeApiService.analyzeAsync(eq("aiM03_utilityIncidentCoordination"), anyMap()))
                    .thenReturn(CompletableFuture.completedFuture(
                            mockDecision("CREATE_MAINTENANCE_TICKET", "MEDIUM")));

            EsgAnomalyDto anomaly = new EsgAnomalyDto("energy", 450.0, 200.0, "BLDG-001", null);
            when(esgService.detectUtilityAnomalies()).thenReturn(List.of(anomaly));

            // Act — trigger scheduled check
            scheduledTriggerService.checkScheduledTriggers();

            await().atMost(10, SECONDS).until(() ->
                    historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey("aiM03_utilityIncidentCoordination")
                            .finished()
                            .count() >= 1
            );
        }

        @Test
        @DisplayName("AI-M04: ESG anomaly detected → process completes")
        void aiM04_esgAnomaly_processCompletes() {
            when(claudeApiService.analyzeAsync(eq("aiM04_esgAnomalyInvestigation"), anyMap()))
                    .thenReturn(CompletableFuture.completedFuture(
                            mockDecision("INVESTIGATE_SPIKE", "MEDIUM")));

            EsgAnomalyDto anomaly = new EsgAnomalyDto("carbon_emission", 120.0, 80.0, null, "2026-Q1");
            when(esgService.detectEsgAnomalies()).thenReturn(List.of(anomaly));

            scheduledTriggerService.checkScheduledTriggers();

            await().atMost(10, SECONDS).until(() ->
                    historyService.createHistoricProcessInstanceQuery()
                            .processDefinitionKey("aiM04_esgAnomalyInvestigation")
                            .finished()
                            .count() >= 1
            );
        }
    }

    // ─── REST-triggered scenario (C02) ────────────────────────────────────

    @Nested
    @DisplayName("REST Generic Trigger")
    class RestTriggerTests {

        @Test
        @DisplayName("AI-C02: REST trigger via config → process completes")
        void aiC02_restTrigger_processCompletes() {
            when(claudeApiService.analyzeAsync(eq("aiC02_citizenServiceRequest"), anyMap()))
                    .thenReturn(CompletableFuture.completedFuture(
                            mockDecision("ASSIGN_TO_ENVIRONMENT", "MEDIUM")));

            // Simulate what GenericRestTriggerController does:
            // lookup config → map variables → start process
            TriggerConfig config = configRepo.findByScenarioKey("aiC02_citizenServiceRequest")
                    .orElseThrow(() -> new AssertionError("Config not found: aiC02"));

            assertThat(config.getTriggerType()).isEqualTo("REST");
            assertThat(config.getEnabled()).isTrue();

            // Start process with variables as REST trigger would
            Map<String, Object> variables = Map.of(
                    "scenarioKey", "aiC02_citizenServiceRequest",
                    "citizenId", "citizen-rest-001",
                    "requestId", java.util.UUID.randomUUID().toString(),
                    "requestType", "ENVIRONMENT",
                    "description", "Bad smell near factory",
                    "district", "D7"
            );

            var instance = workflowService.startProcess(config.getProcessKey(), variables);
            assertThat(instance.getId()).isNotNull();

            assertProcessCompleted(instance.getId());
        }
    }

    // ─── Config CRUD verification ─────────────────────────────────────────

    @Nested
    @DisplayName("Trigger Config Verification")
    class ConfigVerificationTests {

        @Test
        @DisplayName("All 7 trigger configs exist in database")
        void allSevenConfigsExist() {
            List<TriggerConfig> configs = configRepo.findAll();
            assertThat(configs).hasSize(7);

            List<String> keys = configs.stream()
                    .map(TriggerConfig::getScenarioKey)
                    .sorted()
                    .toList();

            assertThat(keys).containsExactly(
                    "aiC01_aqiCitizenAlert",
                    "aiC02_citizenServiceRequest",
                    "aiC03_floodEmergencyEvacuation",
                    "aiM01_floodResponseCoordination",
                    "aiM02_aqiTrafficControl",
                    "aiM03_utilityIncidentCoordination",
                    "aiM04_esgAnomalyInvestigation"
            );
        }

        @Test
        @DisplayName("All configs have correct trigger types")
        void correctTriggerTypes() {
            assertThat(configRepo.findByTriggerTypeAndEnabled("KAFKA", true)).hasSize(4);
            assertThat(configRepo.findByTriggerTypeAndEnabled("REST", true)).hasSize(1);
            assertThat(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).hasSize(2);
        }
    }
}
