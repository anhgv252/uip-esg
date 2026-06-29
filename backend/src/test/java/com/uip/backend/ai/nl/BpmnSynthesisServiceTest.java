package com.uip.backend.ai.nl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.ai.nl.domain.DraftStatus;
import com.uip.backend.ai.nl.domain.NLParseResult;
import com.uip.backend.ai.nl.domain.Route;
import com.uip.backend.ai.nl.domain.WorkflowDraft;
import com.uip.backend.ai.nl.repository.WorkflowDraftRepository;
import com.uip.backend.ai.nl.validation.BpmnValidationException;
import com.uip.backend.ai.nl.validation.BpmnValidationService;
import com.uip.backend.ai.nl.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BpmnSynthesisService.
 *
 * <p>M5-3 T01: Validates template loading, entity substitution, BR-010 injection, validation, and persistence.
 */
@ExtendWith(MockitoExtension.class)
class BpmnSynthesisServiceTest {

    @Mock
    private BpmnTemplateLibrary templateLibrary;

    @Mock
    private BpmnValidationService validationService;

    @Mock
    private WorkflowDraftRepository draftRepository;

    @Mock
    private ObjectMapper objectMapper;

    private BpmnSynthesisService synthesisService;

    private static final String VALID_TEMPLATE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <process id="flood_response">
            <startEvent id="start" />
            <serviceTask id="notifyResidents" name="Notify residents in {{zone}}" />
            <endEvent id="end" />
          </process>
        </definitions>""";

    private static final String TEMPLATE_WITH_BR010 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <process id="flood_response">
            <startEvent id="start" />
            <userTask id="br010OperatorReview" name="BR-010 Operator Review" />
            <serviceTask id="notifyResidents" name="Notify residents" />
            <endEvent id="end" />
          </process>
        </definitions>""";

    @BeforeEach
    void setUp() {
        synthesisService = new BpmnSynthesisService(templateLibrary, validationService, draftRepository, objectMapper);
    }

    @Test
    void synthesise_validInput_shouldCreateDraft() throws BpmnValidationException {
        // Given
        NLParseResult parseResult = new NLParseResult(
            "flood_response",
            0.95,
            Map.of("zone", "quận 7"),
            Route.CLOUD,
            150L,
            null
        );

        when(templateLibrary.getTemplate("flood_response")).thenReturn(VALID_TEMPLATE);
        when(validationService.validate(anyString())).thenReturn(new ValidationResult(true, java.util.List.of()));
        when(draftRepository.save(any(WorkflowDraft.class))).thenAnswer(i -> i.getArgument(0));

        // When
        WorkflowDraft result = synthesisService.synthesise(parseResult, "tenant1", "user1");

        // Then
        assertNotNull(result);
        assertEquals("flood_response", result.getIntent());
        assertEquals("tenant1", result.getTenantId());
        assertEquals("user1", result.getRequestedBy());
        assertEquals(DraftStatus.PENDING_REVIEW, result.getStatus());
        assertEquals(0.95, result.getConfidence());
        assertTrue(result.getBpmnXml().contains("quận 7"));
        assertTrue(result.getBpmnXml().contains("br010OperatorReview"));

        verify(draftRepository, times(1)).save(any(WorkflowDraft.class));
    }

    @Test
    void synthesise_entitySubstitution_shouldReplaceAllPlaceholders() throws BpmnValidationException {
        // Given
        NLParseResult parseResult = new NLParseResult(
            "flood_response",
            0.92,
            Map.of("zone", "quận 1", "threshold", "50"),
            Route.LOCAL,
            200L,
            null
        );

        String templateWithMultiplePlaceholders = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="flood_response">
                <startEvent id="start" />
                <serviceTask id="task1" name="Alert zone {{zone}} with threshold {{threshold}}" />
                <serviceTask id="task2" name="Unknown {{unknownEntity}} should be empty" />
                <endEvent id="end" />
              </process>
            </definitions>""";

        when(templateLibrary.getTemplate("flood_response")).thenReturn(templateWithMultiplePlaceholders);
        when(validationService.validate(anyString())).thenReturn(new ValidationResult(true, java.util.List.of()));
        when(draftRepository.save(any(WorkflowDraft.class))).thenAnswer(i -> i.getArgument(0));

        // When
        WorkflowDraft result = synthesisService.synthesise(parseResult, "tenant1", "user1");

        // Then
        assertTrue(result.getBpmnXml().contains("Alert zone quận 1 with threshold 50"));
        assertTrue(result.getBpmnXml().contains("Unknown  should be empty")); // {{unknownEntity}} replaced with empty
        assertFalse(result.getBpmnXml().contains("{{"));
    }

    @Test
    void synthesise_templateWithBR010_shouldNotDuplicate() throws BpmnValidationException {
        // Given
        NLParseResult parseResult = new NLParseResult(
            "flood_response",
            0.88,
            Map.of(),
            Route.CLOUD,
            120L,
            null
        );

        when(templateLibrary.getTemplate("flood_response")).thenReturn(TEMPLATE_WITH_BR010);
        when(validationService.validate(anyString())).thenReturn(new ValidationResult(true, java.util.List.of()));
        when(draftRepository.save(any(WorkflowDraft.class))).thenAnswer(i -> i.getArgument(0));

        // When
        WorkflowDraft result = synthesisService.synthesise(parseResult, "tenant1", "user1");

        // Then
        long br010Count = result.getBpmnXml().lines()
            .filter(line -> line.contains("br010OperatorReview"))
            .count();
        assertEquals(1, br010Count, "BR-010 review task should appear exactly once");
    }

    @Test
    void synthesise_validationFailure_shouldThrowException() throws BpmnValidationException {
        // Given
        NLParseResult parseResult = new NLParseResult(
            "flood_response",
            0.90,
            Map.of("zone", "quận 3"),
            Route.CLOUD,
            100L,
            null
        );

        when(templateLibrary.getTemplate("flood_response")).thenReturn(VALID_TEMPLATE);
        when(validationService.validate(anyString()))
            .thenReturn(new ValidationResult(false, java.util.List.of("Too many nodes", "scriptTask not allowed")));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> synthesisService.synthesise(parseResult, "tenant1", "user1"));

        assertTrue(exception.getMessage().contains("BPMN validation failed"));
        assertTrue(exception.getMessage().contains("Too many nodes"));
        verify(draftRepository, never()).save(any());
    }

    @Test
    void synthesise_templateNotFound_shouldThrowException() {
        // Given
        NLParseResult parseResult = new NLParseResult(
            "unknown_intent",
            0.70,
            Map.of(),
            Route.CLOUD,
            80L,
            null
        );

        when(templateLibrary.getTemplate("unknown_intent")).thenReturn(null);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> synthesisService.synthesise(parseResult, "tenant1", "user1"));

        assertEquals("No BPMN template found for intent: unknown_intent", exception.getMessage());
        verify(validationService, never()).validate(anyString());
        verify(draftRepository, never()).save(any());
    }
}
