package com.uip.backend.workflow.delegate;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * Placeholder delegate for BPMN service tasks that are not yet implemented.
 * Logs the execution and continues without error.
 * Replace with real delegates as scenarios are implemented in S4-02+.
 */
@Component("placeholderDelegate")
@Slf4j
public class PlaceholderDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        log.info("[PLACEHOLDER] Process: {}, Activity: {}",
            execution.getProcessDefinitionId(), execution.getCurrentActivityName());
    }
}
