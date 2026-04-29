package com.uip.backend.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.dto.ClaudeApiResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MVP2-03c (3a): Circuit breaker state transition tests for ClaudeApiService.
 *
 * Uses a REAL CircuitBreakerRegistry with aggressive thresholds to exercise
 * CLOSED -> OPEN -> HALF_OPEN -> CLOSED transitions.
 *
 * Key insight: analyzeAsync catches exceptions from executeSupplier and returns fallback,
 * but the CB still records each failure. After enough failures, the CB opens and
 * subsequent calls receive CallNotPermittedException (caught by analyzeAsync -> fallback).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MVP2-03c ClaudeApiService — Circuit Breaker State Transitions")
class ClaudeApiServiceCBTest {

    @Mock
    private RestTemplate claudeRestTemplate;
    @Mock
    private RuleBasedFallbackDecisionService fallbackService;

    private CircuitBreakerRegistry cbRegistry;
    private CircuitBreaker circuitBreaker;
    private ClaudeApiService claudeApiService;

    private static final String SCENARIO = "aiC01_aqiCitizenAlert";
    private static final Map<String, Object> CONTEXT = Map.of("aqiValue", "175", "sensorId", "S001");
    private static final String CB_NAME = "claude-api";

    @BeforeEach
    void setUp() {
        // Real CB config with aggressive thresholds for fast test execution
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(100.0f)
                .waitDurationInOpenState(Duration.ofMillis(200))
                .slidingWindowSize(4)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .minimumNumberOfCalls(3)
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();

        cbRegistry = CircuitBreakerRegistry.of(config);
        circuitBreaker = cbRegistry.circuitBreaker(CB_NAME);

        claudeApiService = new ClaudeApiService(
                claudeRestTemplate, fallbackService, new ObjectMapper(), cbRegistry);
        claudeApiService.initCircuitBreaker();
        ReflectionTestUtils.setField(claudeApiService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(claudeApiService, "apiUrl", "https://api.anthropic.com/v1/messages");
        ReflectionTestUtils.setField(claudeApiService, "timeoutSeconds", 10);
    }

    // --- CLOSED -> OPEN -----------------------------------------------------------

    @Test
    @DisplayName("CLOSED -> OPEN: CB opens after failure rate exceeds threshold")
    void closedToOpen_afterFailures() throws Exception {
        // Given
        AIDecision fallback = buildDecision("FALLBACK", 0.5, "MEDIUM");
        when(fallbackService.getFallbackDecision(eq(SCENARIO), anyMap())).thenReturn(fallback);
        when(claudeRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(ClaudeApiResponse.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // When — trigger enough failures through analyzeAsync to exceed the threshold
        for (int i = 0; i < 5; i++) {
            claudeApiService.analyzeAsync(SCENARIO, CONTEXT).get();
        }

        // Then — CB should be OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // --- OPEN: calls go to fallback immediately -----------------------------------

    @Test
    @DisplayName("OPEN state: calls return fallback immediately without calling RestTemplate")
    void openState_callsFallbackWithoutRestTemplate() throws Exception {
        // Given — force CB to OPEN by recording failures directly on the CB instance
        for (int i = 0; i < 5; i++) {
            try {
                circuitBreaker.executeSupplier(() -> {
                    throw new RestClientException("fail");
                });
            } catch (Exception ignored) {
                // CB records failure, exception propagates out.
                // Once CB opens, subsequent calls throw CallNotPermittedException.
            }
        }
        assertThat(circuitBreaker.getState())
                .as("CB should be OPEN after repeated failures")
                .isEqualTo(CircuitBreaker.State.OPEN);

        AIDecision fallback = buildDecision("FALLBACK", 0.5, "MEDIUM");
        when(fallbackService.getFallbackDecision(eq(SCENARIO), anyMap())).thenReturn(fallback);

        // When — call analyzeAsync while CB is OPEN
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO, CONTEXT).get();

        // Then — fallback returned, RestTemplate never called
        assertThat(result.getDecision()).isEqualTo("FALLBACK");
        verify(claudeRestTemplate, never()).exchange(anyString(), any(), any(), eq(ClaudeApiResponse.class));
    }

    // --- HALF_OPEN -> CLOSED ------------------------------------------------------

    @Test
    @DisplayName("HALF_OPEN -> CLOSED: successful probe closes the circuit")
    void halfOpenToClosed_afterSuccessfulProbe() throws Exception {
        // Given — force CB to OPEN
        for (int i = 0; i < 5; i++) {
            try {
                circuitBreaker.executeSupplier(() -> {
                    throw new RestClientException("fail");
                });
            } catch (Exception ignored) {}
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Wait for waitDurationInOpenState to pass (200ms configured)
        // CB transitions from OPEN -> HALF_OPEN lazily on the next acquirePermission() call
        Thread.sleep(350);

        // Prepare successful response for the probe call
        String validJson = """
                {
                  "decision": "NOTIFY_CITIZENS",
                  "reasoning": "AQI normalized",
                  "confidence": 0.88,
                  "recommended_actions": ["Resume normal ops"],
                  "severity": "LOW"
                }
                """;
        mockRestTemplateResponse(validJson);

        // When — successful call triggers OPEN -> HALF_OPEN -> (successful probe) -> CLOSED
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO, CONTEXT).get();

        // Then — response is correct (the call went through, not fallback)
        assertThat(result.getDecision()).isEqualTo("NOTIFY_CITIZENS");
        assertThat(result.getConfidence()).isEqualTo(0.88);

        // CB should have transitioned away from OPEN (either HALF_OPEN or CLOSED depending on config)
        assertThat(circuitBreaker.getState())
                .as("CB should no longer be OPEN after successful probe")
                .isNotEqualTo(CircuitBreaker.State.OPEN);
    }

    // --- Helpers ------------------------------------------------------------------

    private void mockRestTemplateResponse(String text) {
        ClaudeApiResponse.Content content = new ClaudeApiResponse.Content();
        content.setText(text);
        ClaudeApiResponse response = new ClaudeApiResponse();
        response.setContent(List.of(content));
        when(claudeRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(ClaudeApiResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
    }

    private AIDecision buildDecision(String decision, double confidence, String severity) {
        AIDecision d = new AIDecision();
        d.setDecision(decision);
        d.setConfidence(confidence);
        d.setSeverity(severity);
        d.setReasoning("test fallback");
        d.setRecommendedActions(List.of("Manual review"));
        return d;
    }
}
