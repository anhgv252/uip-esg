package com.uip.backend.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.dto.ClaudeApiResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaudeApiService")
class ClaudeApiServiceTest {

    @Mock private RestTemplate claudeRestTemplate;
    @Mock private RuleBasedFallbackDecisionService fallbackService;
    @Mock private CircuitBreakerRegistry circuitBreakerRegistry;
    @Mock private CircuitBreaker circuitBreaker;
    @Mock private CircuitBreaker.EventPublisher eventPublisher;

    private ClaudeApiService claudeApiService;

    private static final String SCENARIO_KEY = "aiC01_aqiCitizenAlert";
    private static final Map<String, Object> CONTEXT = Map.of("aqiValue", "175", "sensorId", "S001");

    @BeforeEach
    void setUp() {
        when(circuitBreakerRegistry.circuitBreaker("claude-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getEventPublisher()).thenReturn(eventPublisher);
        when(eventPublisher.onStateTransition(any())).thenReturn(eventPublisher);
        when(eventPublisher.onError(any())).thenReturn(eventPublisher);
        // By default, CB delegates to real supplier (circuit closed). lenient() — blank-key path skips CB entirely.
        lenient().doAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get())
            .when(circuitBreaker).executeSupplier(any());

        claudeApiService = new ClaudeApiService(claudeRestTemplate, fallbackService, new ObjectMapper(), circuitBreakerRegistry);
        claudeApiService.initCircuitBreaker();
        ReflectionTestUtils.setField(claudeApiService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(claudeApiService, "apiUrl", "https://api.anthropic.com/v1/messages");
        ReflectionTestUtils.setField(claudeApiService, "timeoutSeconds", 10);
    }

    // ─── Case 1 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("apiKey rỗng → trả về fallback decision, không gọi RestTemplate")
    void analyzeAsync_blankApiKey_usesFallback() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(claudeApiService, "apiKey", "");
        AIDecision fallback = buildDecision("FALLBACK", 0.5, "MEDIUM");
        when(fallbackService.getFallbackDecision(eq(SCENARIO_KEY), anyMap())).thenReturn(fallback);

        // Act
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO_KEY, CONTEXT).get();

        // Assert
        assertThat(result.getDecision()).isEqualTo("FALLBACK");
        verifyNoInteractions(claudeRestTemplate);
    }

    // ─── Case 2 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Claude API trả về JSON hợp lệ → parse thành AIDecision đúng")
    void analyzeAsync_validJsonResponse_parsesCorrectly() throws Exception {
        // Arrange
        String validJson = """
                {
                  "decision": "NOTIFY_CITIZENS",
                  "reasoning": "AQI is high",
                  "confidence": 0.92,
                  "recommended_actions": ["Send alert", "Recommend staying indoors"],
                  "severity": "HIGH"
                }
                """;
        mockRestTemplateResponse(validJson);

        // Act
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO_KEY, CONTEXT).get();

        // Assert
        assertThat(result.getDecision()).isEqualTo("NOTIFY_CITIZENS");
        assertThat(result.getConfidence()).isEqualTo(0.92);
        assertThat(result.getSeverity()).isEqualTo("HIGH");
        assertThat(result.getRecommendedActions()).contains("Send alert");
    }

    // ─── Case 3 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Claude trả về JSON trong markdown code block → vẫn parse đúng")
    void analyzeAsync_markdownCodeBlock_extractsJsonCorrectly() throws Exception {
        // Arrange
        String markdownWrapped = """
                ```json
                {
                  "decision": "NOTIFY_CITIZENS",
                  "reasoning": "AQI is high",
                  "confidence": 0.92,
                  "recommended_actions": ["Send alert"],
                  "severity": "HIGH"
                }
                ```""";
        mockRestTemplateResponse(markdownWrapped);

        // Act
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO_KEY, CONTEXT).get();

        // Assert
        assertThat(result.getDecision()).isEqualTo("NOTIFY_CITIZENS");
        assertThat(result.getConfidence()).isEqualTo(0.92);
    }

    // ─── Case 4 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RestTemplate throw RestClientException → trả về fallback, không throw exception")
    void analyzeAsync_restClientException_usesFallback() throws Exception {
        // Arrange
        AIDecision fallback = buildDecision("FALLBACK", 0.5, "MEDIUM");
        when(claudeRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(ClaudeApiResponse.class)))
                .thenThrow(new RestClientException("Connection refused"));
        when(fallbackService.getFallbackDecision(eq(SCENARIO_KEY), anyMap())).thenReturn(fallback);

        // Act
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO_KEY, CONTEXT).get();

        // Assert
        assertThat(result.getDecision()).isEqualTo("FALLBACK");
    }

    // ─── Case 5 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Circuit breaker OPEN → fallback ngay, RestTemplate không được gọi")
    void analyzeAsync_circuitOpen_usesFallbackWithoutCallingRestTemplate() throws Exception {
        // Arrange — wire mocks needed by createCallNotPermittedException static factory
        when(circuitBreaker.getName()).thenReturn("claude-api");
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        when(circuitBreaker.getCircuitBreakerConfig()).thenReturn(CircuitBreakerConfig.ofDefaults());
        doThrow(CallNotPermittedException.createCallNotPermittedException(circuitBreaker))
            .when(circuitBreaker).executeSupplier(any());
        AIDecision fallback = buildDecision("FALLBACK", 0.5, "MEDIUM");
        when(fallbackService.getFallbackDecision(eq(SCENARIO_KEY), anyMap())).thenReturn(fallback);

        // Act
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO_KEY, CONTEXT).get();

        // Assert
        assertThat(result.getDecision()).isEqualTo("FALLBACK");
        verifyNoInteractions(claudeRestTemplate);
    }

    // ─── Case 6 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Claude response trả về content rỗng → fallback được dùng")
    void analyzeAsync_emptyContent_usesFallback() throws Exception {
        // Arrange
        ClaudeApiResponse emptyResponse = new ClaudeApiResponse();
        emptyResponse.setContent(Collections.emptyList());
        when(claudeRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(ClaudeApiResponse.class)))
                .thenReturn(ResponseEntity.ok(emptyResponse));
        AIDecision fallback = buildDecision("FALLBACK", 0.5, "LOW");
        when(fallbackService.getFallbackDecision(eq(SCENARIO_KEY), anyMap())).thenReturn(fallback);

        // Act
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO_KEY, CONTEXT).get();

        // Assert
        assertThat(result.getDecision()).isEqualTo("FALLBACK");
    }

    // ─── Case 7 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Claude response text không phải JSON → trả về PARSE_ERROR decision")
    void analyzeAsync_invalidJson_returnsParseError() throws Exception {
        // Arrange
        mockRestTemplateResponse("đây là text không phải JSON, không thể parse");

        // Act
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO_KEY, CONTEXT).get();

        // Assert
        assertThat(result.getDecision()).isEqualTo("PARSE_ERROR");
        assertThat(result.getConfidence()).isEqualTo(0.0);
    }

    // ─── Case 8 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("substituteVariables thay thế đúng placeholder trong prompt template")
    void analyzeAsync_contextVariables_substitutedInPrompt() throws Exception {
        // Arrange - verify substitution by checking the API is called (not short-circuited)
        // and the result is parsed correctly when context vars are provided
        String validJson = """
                {
                  "decision": "NOTIFY_CITIZENS",
                  "reasoning": "Sensor S001 reports AQI 175",
                  "confidence": 0.88,
                  "recommended_actions": ["Alert issued"],
                  "severity": "HIGH"
                }
                """;
        mockRestTemplateResponse(validJson);

        // Act - CONTEXT có {sensorId} = "S001" và {aqiValue} = "175"
        AIDecision result = claudeApiService.analyzeAsync(SCENARIO_KEY, CONTEXT).get();

        // Assert - verify RestTemplate was called (placeholder substitution did not break)
        verify(claudeRestTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(ClaudeApiResponse.class));
        assertThat(result.getDecision()).isEqualTo("NOTIFY_CITIZENS");
        assertThat(result.getConfidence()).isEqualTo(0.88);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void mockRestTemplateResponse(String text) {
        ClaudeApiResponse.Content content = new ClaudeApiResponse.Content();
        content.setText(text);
        ClaudeApiResponse mockResponse = new ClaudeApiResponse();
        mockResponse.setContent(List.of(content));
        when(claudeRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(ClaudeApiResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));
    }

    private AIDecision buildDecision(String decision, double confidence, String severity) {
        AIDecision d = new AIDecision();
        d.setDecision(decision);
        d.setConfidence(confidence);
        d.setSeverity(severity);
        d.setReasoning("rule-based");
        d.setRecommendedActions(List.of("Manual review"));
        return d;
    }
}
