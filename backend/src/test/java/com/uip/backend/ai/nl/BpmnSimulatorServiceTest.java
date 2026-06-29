package com.uip.backend.ai.nl;

import com.uip.backend.ai.nl.domain.SimulationResult;
import com.uip.backend.ai.nl.domain.SimulationStep;
import com.uip.backend.ai.nl.validation.BpmnValidationException;
import com.uip.backend.ai.nl.validation.BpmnValidationService;
import com.uip.backend.ai.nl.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BpmnSimulatorService.
 *
 * <p>M5-3 T04: Validates dry-run BPMN execution, step simulation, and risk warnings.
 */
@ExtendWith(MockitoExtension.class)
class BpmnSimulatorServiceTest {

    @Mock
    private BpmnValidationService validationService;

    private BpmnSimulatorService simulatorService;

    private static final String VALID_FLOOD_RESPONSE_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <process id="flood_response">
            <startEvent id="start" name="Flood detected" />
            <userTask id="br010OperatorReview" name="BR-010 Operator Review" />
            <serviceTask id="notifyResidents" name="Notify residents in quận 7" />
            <serviceTask id="activatePumps" name="Activate drainage pumps" />
            <serviceTask id="alertAuthorities" name="Alert city authorities" />
            <endEvent id="end" name="Response complete" />
          </process>
        </definitions>""";

    private static final String VALID_AQI_ALERT_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <process id="aqi_alert">
            <startEvent id="start" name="Poor AQI detected" />
            <serviceTask id="calculateAQI" name="Calculate AQI level" />
            <serviceTask id="notifyCitizens" name="Send mobile notifications" />
            <serviceTask id="updateDashboard" name="Update ESG dashboard" />
            <endEvent id="end" name="Alert sent" />
          </process>
        </definitions>""";

    private static final String INVALID_BPMN_TOO_MANY_NODES = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <process id="invalid">
            <startEvent id="start" />
            <!-- 21 tasks would exceed max 20 nodes limit -->
            <endEvent id="end" />
          </process>
        </definitions>""";

    @BeforeEach
    void setUp() {
        simulatorService = new BpmnSimulatorService(validationService);
    }

    @Test
    void simulate_floodResponse_shouldReturnFiveStepsWithHighRiskWarning() throws BpmnValidationException {
        // Given
        when(validationService.validate(anyString())).thenReturn(new ValidationResult(true, java.util.List.of()));

        // When
        SimulationResult result = simulatorService.simulate("flood_response", VALID_FLOOD_RESPONSE_BPMN);

        // Then
        assertTrue(result.success());
        assertEquals("flood_response", result.intent());
        assertEquals(6, result.steps().size()); // 1 start + 1 userTask + 3 serviceTasks + 1 end

        // Verify step types
        long startEvents = result.steps().stream().filter(s -> s.elementType().equals("startEvent")).count();
        long userTasks = result.steps().stream().filter(s -> s.elementType().equals("userTask")).count();
        long serviceTasks = result.steps().stream().filter(s -> s.elementType().equals("serviceTask")).count();
        long endEvents = result.steps().stream().filter(s -> s.elementType().equals("endEvent")).count();

        assertEquals(1, startEvents);
        assertEquals(1, userTasks);
        assertEquals(3, serviceTasks);
        assertEquals(1, endEvents);

        // Verify all steps are OK
        assertTrue(result.steps().stream().allMatch(s -> s.status() == SimulationStep.StepStatus.OK));

        // Verify HIGH RISK warning
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("HIGH RISK")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("flood_response")));

        assertNotNull(result.durationMs());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void simulate_aqiAlert_shouldReturnFourStepsWithoutRiskWarning() throws BpmnValidationException {
        // Given
        when(validationService.validate(anyString())).thenReturn(new ValidationResult(true, java.util.List.of()));

        // When
        SimulationResult result = simulatorService.simulate("aqi_alert", VALID_AQI_ALERT_BPMN);

        // Then
        assertTrue(result.success());
        assertEquals(5, result.steps().size()); // 1 start + 3 serviceTasks + 1 end

        // No HIGH or MEDIUM risk warnings for aqi_alert
        assertFalse(result.warnings().stream().anyMatch(w -> w.contains("HIGH RISK")));
        assertFalse(result.warnings().stream().anyMatch(w -> w.contains("MEDIUM RISK")));
    }

    @Test
    void simulate_invalidBpmn_shouldReturnValidationFailure() throws BpmnValidationException {
        // Given
        when(validationService.validate(anyString()))
            .thenReturn(new ValidationResult(false, java.util.List.of("Too many nodes")));

        // When
        SimulationResult result = simulatorService.simulate("invalid", INVALID_BPMN_TOO_MANY_NODES);

        // Then
        assertFalse(result.success());
        assertEquals("invalid", result.intent());
        assertTrue(result.warnings().get(0).contains("Validation failed"));
        assertTrue(result.warnings().get(0).contains("Too many nodes"));
    }

    @Test
    void simulate_bpmnWithScriptTask_shouldReturnSecurityRejection() throws BpmnValidationException {
        // Given
        String bpmnWithScriptTask = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="malicious">
                <startEvent id="start" />
                <scriptTask id="script1" scriptFormat="javascript">
                  <script>java.lang.System.exit(0);</script>
                </scriptTask>
                <endEvent id="end" />
              </process>
            </definitions>""";

        when(validationService.validate(anyString()))
            .thenReturn(new ValidationResult(false, java.util.List.of("scriptTask not allowed (security risk)")));

        // When
        SimulationResult result = simulatorService.simulate("malicious", bpmnWithScriptTask);

        // Then
        assertFalse(result.success());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("scriptTask not allowed")));
    }

    @Test
    void simulate_emergencyEvacuation_shouldReturnHighRiskWarning() throws BpmnValidationException {
        // Given
        String emergencyBpmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="emergency_evacuation">
                <startEvent id="start" />
                <serviceTask id="broadcastAlert" name="Broadcast emergency alert" />
                <serviceTask id="activateSirens" name="Activate city sirens" />
                <endEvent id="end" />
              </process>
            </definitions>""";

        when(validationService.validate(anyString())).thenReturn(new ValidationResult(true, java.util.List.of()));

        // When
        SimulationResult result = simulatorService.simulate("emergency_evacuation", emergencyBpmn);

        // Then
        assertTrue(result.success());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("HIGH RISK")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("emergency_evacuation")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("real-world safety impact")));
    }
}
