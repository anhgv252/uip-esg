package com.uip.backend.workflow.delegate.management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AI-M01: Flood Response Coordination Delegate
 * Coordinates emergency response teams for flood incidents
 */
@Component("floodResponseDelegate")
@RequiredArgsConstructor
@Slf4j
public class FloodResponseDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String alertId = (String) execution.getVariable("alertId");
        Object waterLevel = execution.getVariable("waterLevel");
        String location = (String) execution.getVariable("location");
        String aiReasoning = (String) execution.getVariable("aiReasoning");
        String aiSeverity = (String) execution.getVariable("aiSeverity");

        log.info("Flood Response Coordination for alert {}: location={}, severity={}", 
                alertId, location, aiSeverity);

        // Generate operations log entry
        String operationsLogId = UUID.randomUUID().toString();
        
        // Log emergency coordination ticket
        log.info("Emergency ticket created: {} - Flood at {} (water level: {}). AI reasoning: {}", 
                operationsLogId, location, waterLevel, aiReasoning);

        // Dispatch team based on severity
        boolean teamDispatched = !"LOW".equalsIgnoreCase(aiSeverity);

        execution.setVariable("teamDispatched", teamDispatched);
        execution.setVariable("operationsLogId", operationsLogId);

        if (teamDispatched) {
            log.info("Emergency response team dispatched for flood at: {}", location);
        } else {
            log.info("Monitoring flood situation at: {} - no immediate dispatch required", location);
        }
    }
}
