package com.uip.backend.workflow;

import com.uip.backend.esg.service.EsgService;
import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.ClaudeApiService;
import com.uip.backend.workflow.service.WorkflowService;
import org.camunda.bpm.engine.HistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.cache.type=simple",
        "spring.autoconfigure.exclude=" +
            "org.camunda.bpm.spring.boot.starter.rest.CamundaBpmRestJerseyAutoConfiguration," +
            "org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration," +
            "org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration"
    }
)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("S4-04 Management AI Scenarios — Integration")
class ManagementAiScenariosIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("uip")
            .withPassword("test_password");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6399");
        registry.add("security.jwt.secret",
                () -> java.util.Base64.getEncoder().encodeToString(
                        "uip-integration-test-secret-32b!".getBytes()));
    }

    @MockBean @SuppressWarnings("unused")
    RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    StringRedisTemplate redisTemplate;
    @MockBean @SuppressWarnings("unused")
    RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused")
    KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean private ClaudeApiService claudeApiService;
    @MockBean private EsgService esgService;

    @Autowired private WorkflowService workflowService;
    @Autowired private HistoryService  historyService;

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
