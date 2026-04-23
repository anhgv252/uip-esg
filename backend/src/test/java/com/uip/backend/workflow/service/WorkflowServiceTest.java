package com.uip.backend.workflow.service;

import com.uip.backend.common.exception.WorkflowNotFoundException;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowService")
class WorkflowServiceTest {

    @Mock private RuntimeService runtimeService;

    // Not needed for startProcess tests, but required by constructor
    @Mock private org.camunda.bpm.engine.RepositoryService repositoryService;
    @Mock private org.camunda.bpm.engine.HistoryService historyService;

    @InjectMocks private WorkflowService workflowService;

    // ─── startProcess ────────────────────────────────────────────────────────

    @Test
    @DisplayName("startProcess returns ProcessInstanceDto on success")
    void startProcess_validKey_returnsInstanceDto() {
        // Arrange
        ProcessInstance mockInstance = mock(ProcessInstance.class);
        when(mockInstance.getId()).thenReturn("pi-12345");
        when(mockInstance.getProcessDefinitionId()).thenReturn("pd-aiC01:1:999");
        when(mockInstance.getBusinessKey()).thenReturn(null);
        when(runtimeService.startProcessInstanceByKey(eq("aiC01_aqiCitizenAlert"), anyMap()))
            .thenReturn(mockInstance);
        when(runtimeService.getVariables("pi-12345")).thenReturn(Map.of("scenarioKey", "aiC01"));

        // Act
        ProcessInstanceDto result = workflowService.startProcess(
            "aiC01_aqiCitizenAlert", Map.of("scenarioKey", "aiC01"));

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("pi-12345");
        assertThat(result.getState()).isEqualTo("ACTIVE");
        verify(runtimeService).startProcessInstanceByKey(eq("aiC01_aqiCitizenAlert"), anyMap());
    }

    @Test
    @DisplayName("startProcess throws WorkflowNotFoundException for invalid process key")
    void startProcess_invalidKey_throwsWorkflowNotFound() {
        // Arrange
        when(runtimeService.startProcessInstanceByKey(eq("nonexistent-process"), anyMap()))
            .thenThrow(new ProcessEngineException("no processes deployed with key 'nonexistent-process'"));

        // Act + Assert
        assertThatThrownBy(() -> workflowService.startProcess("nonexistent-process", Map.of()))
            .isInstanceOf(WorkflowNotFoundException.class)
            .hasMessageContaining("nonexistent-process");
    }
}
