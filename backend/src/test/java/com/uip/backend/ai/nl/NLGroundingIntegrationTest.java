package com.uip.backend.ai.nl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.ai.nl.domain.NLParseRequest;
import com.uip.backend.ai.nl.domain.NLParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for NL→BPMN grounding with test corpus.
 * 
 * <p>M5-2 T02 + T03: Validate ≥80% intent hit rate across 50 test sentences.
 */
@SpringBootTest(
    classes = NLModuleTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.data.redis.enabled=false",
        "spring.cache.type=none"
    })
@ActiveProfiles("test")
@DisplayName("NL Grounding Integration Tests")
class NLGroundingIntegrationTest {

    @Autowired
    private NLIntentParserService parserService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    @DisplayName("Test corpus: ≥80% intent accuracy (50 sentences, gdprMode=true)")
    void testCorpusIntentAccuracy() throws Exception {
        // Load test corpus
        TestCorpus corpus = loadCorpus();
        
        int total = corpus.testCases().size();
        int correct = 0;
        
        for (TestCase testCase : corpus.testCases()) {
            String text = testCase.text();
            String expectedIntent = testCase.expectedIntent();
            
            NLParseRequest request = new NLParseRequest(
                text,
                null,
                true, // gdprMode → LOCAL parser (deterministic for testing)
                "test-tenant",
                "test-" + System.nanoTime()
            );
            
            try {
                NLParseResult result = parserService.parse(request);
                
                if (expectedIntent.equals(result.intent())) {
                    correct++;
                } else {
                    System.err.printf("MISS: text='%s' expected=%s got=%s%n",
                        text, expectedIntent, result.intent());
                }
                
            } catch (Exception e) {
                System.err.printf("ERROR: text='%s' error=%s%n", text, e.getMessage());
            }
        }
        
        double accuracy = (double) correct / total;
        
        System.out.printf("Intent accuracy: %d/%d = %.1f%%%n", correct, total, accuracy * 100);
        
        assertThat(accuracy).isGreaterThanOrEqualTo(0.80)
            .withFailMessage("Intent accuracy %.1f%% below 80%% threshold", accuracy * 100);
    }
    
    @Test
    @DisplayName("BPMN template instantiation for all 10 intents")
    void testBpmnTemplateInstantiation() {
        String[] intents = {
            "flood_response", "aqi_alert", "traffic_signal", "building_hvac",
            "sensor_maintenance", "citizen_notification", "energy_optimization",
            "water_leak_response", "emergency_evacuation", "esg_report"
        };
        
        for (String intent : intents) {
            NLParseRequest request = new NLParseRequest(
                "Test " + intent,
                null,
                true,
                "test-tenant",
                "test-" + intent
            );
            
            try {
                NLParseResult result = parserService.parse(request);
                
                assertThat(result.bpmnTemplate())
                    .as("BPMN template for intent %s", intent)
                    .isNotNull()
                    .contains("<?xml")
                    .contains("<bpmn:process");
                    
            } catch (Exception e) {
                throw new AssertionError("Failed to instantiate BPMN for intent: " + intent, e);
            }
        }
    }
    
    private TestCorpus loadCorpus() throws IOException {
        ClassPathResource resource = new ClassPathResource("nl/intent-test-corpus.json");
        return objectMapper.readValue(resource.getInputStream(), TestCorpus.class);
    }
    
    record TestCorpus(List<TestCase> testCases) {}
    
    record TestCase(String text, String expectedIntent) {}
}
