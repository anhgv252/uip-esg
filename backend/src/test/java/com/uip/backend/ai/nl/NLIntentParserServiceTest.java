package com.uip.backend.ai.nl;

import com.uip.backend.ai.nl.domain.NLParseRequest;
import com.uip.backend.ai.nl.domain.NLParseResult;
import com.uip.backend.ai.nl.domain.Route;
import com.uip.backend.ai.nl.domain.RoutingDecision;
import com.uip.backend.ai.nl.domain.RoutingReason;
import com.uip.backend.ai.nl.validation.BpmnValidationService;
import com.uip.backend.ai.nl.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NLIntentParserService.
 * 
 * <p>M5-2 T02: Tests routing + parser invocation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NL Intent Parser Service Unit Tests")
class NLIntentParserServiceTest {

    @Mock
    private ModelRouter modelRouter;
    
    @Mock
    private ClaudeNLParser claudeParser;
    
    @Mock
    private LocalNLParser localParser;
    
    @Mock
    private BpmnTemplateLibrary templateLibrary;

    @Mock
    private BpmnValidationService bpmnValidationService;
    
    private NLIntentParserService service;
    
    @BeforeEach
    void setUp() {
        service = new NLIntentParserService(
            modelRouter,
            claudeParser,
            templateLibrary,
            bpmnValidationService,
            localParser
        );
        // Default: BPMN validation passes — override in individual tests to test failure paths
        lenient().when(bpmnValidationService.validate(anyString())).thenReturn(ValidationResult.ok());
    }
    
    @Test
    @DisplayName("GDPR mode header → LOCAL route → local parser called")
    void testGdprModeRoutesLocal() throws Exception {
        // Given
        NLParseRequest request = new NLParseRequest(
            "Kích hoạt bơm thoát nước ở quận 1",
            null,
            true, // gdprMode
            "tenant-1",
            "req-123"
        );
        
        RoutingDecision decision = new RoutingDecision(
            Route.LOCAL,
            RoutingReason.GDPR_MODE_HEADER,
            0L
        );
        
        NLParseResult mockResult = new NLParseResult(
            "flood_response",
            0.95,
            Map.of("zone", "quận 1"),
            Route.LOCAL,
            100L,
            null // BPMN template will be filled by service after calling templateLibrary
        );
        
        when(modelRouter.route(request)).thenReturn(decision);
        when(localParser.parse(anyString(), anyString())).thenReturn(mockResult);
        when(templateLibrary.instantiate("flood_response", Map.of("zone", "quận 1")))
            .thenReturn("<bpmn>flood_response_template</bpmn>");
        
        // When
        NLParseResult result = service.parse(request);
        
        // Then
        assertThat(result.intent()).isEqualTo("flood_response");
        assertThat(result.modelUsed()).isEqualTo(Route.LOCAL);
        assertThat(result.bpmnTemplate()).isNotNull();
    }
    
    @Test
    @DisplayName("PII detected → LOCAL route")
    void testPiiDetectionRoutesLocal() throws Exception {
        // Given
        NLParseRequest request = new NLParseRequest(
            "Người dân Nguyễn Văn An CCCD 123456789012",
            null,
            false,
            "tenant-1",
            "req-456"
        );
        
        RoutingDecision decision = new RoutingDecision(
            Route.LOCAL,
            RoutingReason.PII_DETECTED,
            5L
        );
        
        NLParseResult mockResult = new NLParseResult(
            "citizen_notification",
            0.88,
            Map.of(),
            Route.LOCAL,
            150L,
            null
        );
        
        when(modelRouter.route(request)).thenReturn(decision);
        when(localParser.parse(anyString(), anyString())).thenReturn(mockResult);
        when(templateLibrary.instantiate("citizen_notification", Map.of()))
            .thenReturn("<bpmn>citizen_notification_template</bpmn>");
        
        // When
        NLParseResult result = service.parse(request);
        
        // Then
        assertThat(result.intent()).isEqualTo("citizen_notification");
        assertThat(result.modelUsed()).isEqualTo(Route.LOCAL);
    }
    
    @Test
    @DisplayName("Non-PII → CLOUD route → Claude called")
    void testNonPiiRoutesCloud() throws Exception {
        // Given
        NLParseRequest request = new NLParseRequest(
            "Chỉ số AQI vượt 150 ở quận 1",
            null,
            false,
            "tenant-1",
            "req-789"
        );
        
        RoutingDecision decision = new RoutingDecision(
            Route.CLOUD,
            RoutingReason.NON_PII,
            3L
        );
        
        NLParseResult mockResult = new NLParseResult(
            "aqi_alert",
            0.97,
            Map.of("threshold", "150", "zone", "quận 1"),
            Route.CLOUD,
            2500L,
            null
        );
        
        when(modelRouter.route(request)).thenReturn(decision);
        when(claudeParser.parse(anyString(), anyString())).thenReturn(mockResult);
        when(templateLibrary.instantiate("aqi_alert", Map.of("threshold", "150", "zone", "quận 1")))
            .thenReturn("<bpmn>aqi_alert_template</bpmn>");
        
        // When
        NLParseResult result = service.parse(request);
        
        // Then
        assertThat(result.intent()).isEqualTo("aqi_alert");
        assertThat(result.modelUsed()).isEqualTo(Route.CLOUD);
        assertThat(result.confidence()).isGreaterThan(0.9);
    }
}

