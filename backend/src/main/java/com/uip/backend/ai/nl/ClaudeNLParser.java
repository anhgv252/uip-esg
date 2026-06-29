package com.uip.backend.ai.nl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.ai.nl.domain.NLParseResult;
import com.uip.backend.ai.nl.domain.Route;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude API-based Vietnamese NL→BPMN intent parser.
 * 
 * <p>POC implementation: uses Claude as proxy for ViT5 fine-tuned model.
 * <p>Production (M5-4): replace with actual ViT5 HTTP endpoint.
 * 
 * <p>ADR-049 §6: Claude SLA p95 ≤ 4s, timeout 6s, intent hit rate ~92%.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaudeNLParser {

    @Value("${ai.claude.api-key:${CLAUDE_API_KEY:}}")
    private String apiKey;
    
    @Value("${ai.claude.api-url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;
    
    @Value("${ai.claude.model:claude-3-5-sonnet-20241022}")
    private String model;
    
    @Value("${ai.claude.timeout-seconds:6}")
    private int timeoutSeconds;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String SYSTEM_PROMPT = """
        You are a city operations assistant for Vietnamese smart city platform.
        
        Classify the Vietnamese operator command into ONE of these workflow intents:
        1. flood_response — flood/water emergency response
        2. aqi_alert — air quality alert/monitoring
        3. traffic_signal — traffic light control
        4. building_hvac — building HVAC control
        5. sensor_maintenance — sensor maintenance scheduling
        6. citizen_notification — citizen notification broadcast
        7. energy_optimization — building energy optimization
        8. water_leak_response — water leak detection/response
        9. emergency_evacuation — emergency evacuation activation
        10. esg_report — ESG report generation
        
        Extract relevant entities (zone, threshold, building, sensor ID, etc.).
        
        Return JSON ONLY (no markdown, no explanation):
        {
          "intent": "<one of the 10 intents>",
          "confidence": <0.0-1.0>,
          "entities": {"key": "value", ...}
        }
        
        If command is ambiguous or doesn't match any intent, return:
        {"intent": "UNKNOWN", "confidence": 0.0, "entities": {}}
        """;

    /**
     * Parse Vietnamese text via Claude API.
     * 
     * @param text Vietnamese operator command
     * @param requestId for logging
     * @return parse result with intent, confidence, entities
     * @throws NLInferenceException if API call fails or times out
     */
    public NLParseResult parse(String text, String requestId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new NLInferenceException(
                "Claude API key not configured (ai.claude.api-key or CLAUDE_API_KEY)", 
                false
            );
        }
        
        long startMs = System.currentTimeMillis();
        
        try {
            // Build Claude API request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            
            Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 1024,
                "system", SYSTEM_PROMPT,
                "messages", List.of(
                    Map.of("role", "user", "content", text)
                )
            );
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // Call Claude API with timeout
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new NLInferenceException(
                    "Claude API returned non-2xx: " + response.getStatusCode(),
                    true // retryable
                );
            }
            
            // Parse Claude response
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("content").get(0).path("text");
            
            if (content == null || content.isNull()) {
                throw new NLInferenceException(
                    "Claude API response missing content.text",
                    false
                );
            }
            
            String jsonText = content.asText().trim();
            
            // Parse intent JSON from Claude
            JsonNode intentNode = objectMapper.readTree(jsonText);
            String intent = intentNode.path("intent").asText("UNKNOWN");
            double confidence = intentNode.path("confidence").asDouble(0.0);
            
            Map<String, String> entities = new HashMap<>();
            JsonNode entitiesNode = intentNode.path("entities");
            if (entitiesNode.isObject()) {
                entitiesNode.fields().forEachRemaining(entry -> 
                    entities.put(entry.getKey(), entry.getValue().asText())
                );
            }
            
            long latencyMs = System.currentTimeMillis() - startMs;
            
            log.info("Claude parse complete: intent={}, confidence={}, latencyMs={}, requestId={}",
                    intent, confidence, latencyMs, requestId);
            
            return new NLParseResult(
                intent,
                confidence,
                entities.isEmpty() ? null : entities,
                Route.CLOUD,
                latencyMs,
                null // BPMN template filled by service layer
            );
            
        } catch (RestClientException e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            log.error("Claude API call failed: latencyMs={}, requestId={}", 
                     latencyMs, requestId, e);
            throw new NLInferenceException(
                "Claude API call failed: " + e.getMessage(),
                e,
                true // retryable
            );
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            log.error("Claude response parsing failed: latencyMs={}, requestId={}", 
                     latencyMs, requestId, e);
            throw new NLInferenceException(
                "Failed to parse Claude response: " + e.getMessage(),
                e,
                false // not retryable (likely response format issue)
            );
        }
    }
}
