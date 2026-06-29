package com.uip.backend.ai.nl;

import com.uip.backend.ai.nl.domain.SimulationResult;
import com.uip.backend.ai.nl.domain.SimulationStep;
import com.uip.backend.ai.nl.domain.SimulationStep.StepStatus;
import com.uip.backend.ai.nl.validation.BpmnValidationException;
import com.uip.backend.ai.nl.validation.BpmnValidationService;
import com.uip.backend.ai.nl.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * BPMN workflow simulator — dry-run testing without real actuation.
 *
 * <p>M5-3 T04: In-memory BPMN execution for pre-deployment verification.
 *
 * <p>Simulation workflow:
 * <ol>
 *   <li>Validate BPMN structure via BpmnValidationService</li>
 *   <li>Parse BPMN XML to extract flow elements</li>
 *   <li>Walk flow graph in order (startEvent → tasks → endEvent)</li>
 *   <li>For each task: call stub handler (no real execution)</li>
 *   <li>Log each step with status (OK/SKIPPED/FAILED)</li>
 *   <li>Return SimulationResult with warnings for high-risk workflows</li>
 * </ol>
 *
 * <p>Risk warnings:
 * <ul>
 *   <li>emergency_evacuation: HIGH risk — real-world impact if deployed</li>
 *   <li>building_hvac: MEDIUM risk — energy consumption impact</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BpmnSimulatorService {

    private final BpmnValidationService validationService;

    private static final List<String> HIGH_RISK_INTENTS = List.of("emergency_evacuation", "flood_response");
    private static final List<String> MEDIUM_RISK_INTENTS = List.of("building_hvac", "traffic_signal");

    /**
     * Simulate BPMN workflow execution (dry-run).
     *
     * @param intent  Workflow intent type
     * @param bpmnXml BPMN 2.0 XML to simulate
     * @return Simulation result with steps, warnings, and success status
     * @throws IllegalArgumentException if BPMN is invalid or cannot be parsed
     */
    public SimulationResult simulate(String intent, String bpmnXml) {
        long startTime = System.currentTimeMillis();
        log.info("Starting BPMN simulation for intent={}", intent);

        List<String> warnings = new ArrayList<>();
        List<SimulationStep> steps = new ArrayList<>();

        // Step 1: Validate BPMN structure
        try {
            ValidationResult validationResult = validationService.validate(bpmnXml);
            if (!validationResult.valid()) {
                String errors = String.join("; ", validationResult.errors());
                log.error("BPMN validation failed for intent={}: {}", intent, errors);
                return new SimulationResult(intent, false, steps, List.of("Validation failed: " + errors), 
                                           System.currentTimeMillis() - startTime);
            }
        } catch (BpmnValidationException e) {
            log.error("BPMN validation exception for intent={}: {}", intent, e.getMessage());
            return new SimulationResult(intent, false, steps, List.of("Validation error: " + e.getMessage()), 
                                       System.currentTimeMillis() - startTime);
        }

        // Step 2: Parse BPMN and extract flow elements
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            // Extract all flow elements
            steps.addAll(simulateStartEvents(doc));
            steps.addAll(simulateTasks(doc));
            steps.addAll(simulateEndEvents(doc));

        } catch (Exception e) {
            log.error("Failed to parse BPMN XML for intent={}: {}", intent, e.getMessage());
            return new SimulationResult(intent, false, steps, List.of("Parse error: " + e.getMessage()), 
                                       System.currentTimeMillis() - startTime);
        }

        // Step 3: Add risk warnings
        if (HIGH_RISK_INTENTS.contains(intent)) {
            warnings.add("HIGH RISK: " + intent + " workflow has real-world safety impact if deployed");
        } else if (MEDIUM_RISK_INTENTS.contains(intent)) {
            warnings.add("MEDIUM RISK: " + intent + " workflow impacts city resources");
        }

        long durationMs = System.currentTimeMillis() - startTime;
        boolean success = steps.stream().noneMatch(s -> s.status() == StepStatus.FAILED);

        log.info("BPMN simulation completed for intent={}, success={}, steps={}, warnings={}, durationMs={}", 
                 intent, success, steps.size(), warnings.size(), durationMs);

        return new SimulationResult(intent, success, steps, warnings, durationMs);
    }

    private List<SimulationStep> simulateStartEvents(Document doc) {
        List<SimulationStep> steps = new ArrayList<>();
        NodeList startEvents = doc.getElementsByTagName("startEvent");
        
        for (int i = 0; i < startEvents.getLength(); i++) {
            Element element = (Element) startEvents.item(i);
            String id = element.getAttribute("id");
            String name = element.getAttribute("name");
            
            steps.add(new SimulationStep(id, "startEvent", name, StepStatus.OK, "Workflow initiated"));
            log.debug("Simulated startEvent: id={}, name={}", id, name);
        }
        
        return steps;
    }

    private List<SimulationStep> simulateTasks(Document doc) {
        List<SimulationStep> steps = new ArrayList<>();
        
        // Simulate userTask elements
        steps.addAll(simulateTaskType(doc, "userTask"));
        
        // Simulate serviceTask elements
        steps.addAll(simulateTaskType(doc, "serviceTask"));
        
        return steps;
    }

    private List<SimulationStep> simulateTaskType(Document doc, String taskType) {
        List<SimulationStep> steps = new ArrayList<>();
        NodeList tasks = doc.getElementsByTagName(taskType);
        
        for (int i = 0; i < tasks.getLength(); i++) {
            Element element = (Element) tasks.item(i);
            String id = element.getAttribute("id");
            String name = element.getAttribute("name");
            
            // Stub handler — no real execution
            StepStatus status = StepStatus.OK;
            String message = "Simulated " + taskType + " execution (stub handler)";
            
            // Special handling for BR-010 operator review
            if (id.contains("br010") || id.contains("OperatorReview")) {
                message = "Operator review gate detected — would require approval in production";
            }
            
            steps.add(new SimulationStep(id, taskType, name, status, message));
            log.debug("Simulated {}: id={}, name={}", taskType, id, name);
        }
        
        return steps;
    }

    private List<SimulationStep> simulateEndEvents(Document doc) {
        List<SimulationStep> steps = new ArrayList<>();
        NodeList endEvents = doc.getElementsByTagName("endEvent");
        
        for (int i = 0; i < endEvents.getLength(); i++) {
            Element element = (Element) endEvents.item(i);
            String id = element.getAttribute("id");
            String name = element.getAttribute("name");
            
            steps.add(new SimulationStep(id, "endEvent", name, StepStatus.OK, "Workflow completed"));
            log.debug("Simulated endEvent: id={}, name={}", id, name);
        }
        
        return steps;
    }
}
