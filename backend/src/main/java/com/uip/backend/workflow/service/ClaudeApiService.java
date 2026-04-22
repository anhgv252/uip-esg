package com.uip.backend.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.dto.ClaudeApiRequest;
import com.uip.backend.workflow.dto.ClaudeApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClaudeApiService {

    private final RestTemplate claudeRestTemplate;
    private final RuleBasedFallbackDecisionService fallbackService;
    private final ObjectMapper objectMapper;

    @Value("${claude.api.key:}")
    private String apiKey;

    @Value("${claude.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${claude.api.timeout-seconds:10}")
    private int timeoutSeconds;

    @Async
    public CompletableFuture<AIDecision> analyzeAsync(String scenarioKey, Map<String, Object> context) {
        // Check if API key is configured
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Claude API key not configured, using fallback for scenario: {}", scenarioKey);
            return CompletableFuture.completedFuture(fallbackService.getFallbackDecision(scenarioKey, context));
        }

        try {
            // Load prompt template
            String promptTemplate = loadPromptTemplate(scenarioKey);
            
            // Substitute context variables
            String prompt = substituteVariables(promptTemplate, context);
            
            // Build Claude API request
            ClaudeApiRequest request = ClaudeApiRequest.builder()
                    .model("claude-sonnet-4-6")
                    .maxTokens(1024)
                    .messages(List.of(new ClaudeApiRequest.Message("user", prompt)))
                    .build();
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            headers.set("Content-Type", "application/json");
            
            HttpEntity<ClaudeApiRequest> entity = new HttpEntity<>(request, headers);
            
            // Call Claude API
            log.info("Calling Claude API for scenario: {}", scenarioKey);
            ResponseEntity<ClaudeApiResponse> response = claudeRestTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    ClaudeApiResponse.class
            );
            
            // Parse response
            if (response.getBody() != null && response.getBody().getContent() != null 
                    && !response.getBody().getContent().isEmpty()) {
                String responseText = response.getBody().getContent().get(0).getText();
                AIDecision decision = parseAIDecision(responseText);
                log.info("Claude API decision for {}: {}", scenarioKey, decision.getDecision());
                return CompletableFuture.completedFuture(decision);
            } else {
                log.warn("Empty response from Claude API for scenario: {}", scenarioKey);
                return CompletableFuture.completedFuture(fallbackService.getFallbackDecision(scenarioKey, context));
            }
            
        } catch (Exception e) {
            log.error("Error calling Claude API for scenario {}: {}", scenarioKey, e.getMessage());
            return CompletableFuture.completedFuture(fallbackService.getFallbackDecision(scenarioKey, context));
        }
    }

    private String loadPromptTemplate(String scenarioKey) throws IOException {
        String templatePath = "prompts/" + scenarioKey + ".txt";
        ClassPathResource resource = new ClassPathResource(templatePath);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String substituteVariables(String template, Map<String, Object> context) {
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private AIDecision parseAIDecision(String responseText) {
        try {
            // Try to extract JSON from markdown code block if present
            String jsonText = responseText;
            Pattern jsonPattern = Pattern.compile("```json\\s*\\n(.+?)\\n```", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(responseText);
            if (matcher.find()) {
                jsonText = matcher.group(1).trim();
            } else {
                // Try plain ``` blocks
                Pattern plainBlockPattern = Pattern.compile("```\\s*\\n(.+?)\\n```", Pattern.DOTALL);
                Matcher plainMatcher = plainBlockPattern.matcher(responseText);
                if (plainMatcher.find()) {
                    jsonText = plainMatcher.group(1).trim();
                }
            }
            
            return objectMapper.readValue(jsonText, AIDecision.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI decision JSON: {}", e.getMessage());
            // Return a default decision
            AIDecision fallback = new AIDecision();
            fallback.setDecision("PARSE_ERROR");
            fallback.setReasoning("Failed to parse AI response: " + responseText);
            fallback.setConfidence(0.0);
            fallback.setRecommendedActions(List.of("Manual review required"));
            fallback.setSeverity("LOW");
            return fallback;
        }
    }
}
