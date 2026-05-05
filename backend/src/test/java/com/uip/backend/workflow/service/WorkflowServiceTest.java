package com.uip.backend.workflow.service;

import com.uip.backend.common.exception.WorkflowNotFoundException;
import com.uip.backend.workflow.dto.ProcessDefinitionDto;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowService")
class WorkflowServiceTest {

    @Mock private RepositoryService repositoryService;
    @Mock private RuntimeService runtimeService;
    @Mock private HistoryService historyService;

    @InjectMocks private WorkflowService workflowService;

    // ─── listDefinitions ────────────────────────────────────────────────────

    @Nested
    @DisplayName("listDefinitions")
    class ListDefinitions {

        @Test
        @DisplayName("returns mapped DTOs from Camunda query")
        void returnsDtos() {
            ProcessDefinition def = mock(ProcessDefinition.class);
            when(def.getId()).thenReturn("pd-1");
            when(def.getKey()).thenReturn("aiC01");
            when(def.getName()).thenReturn("AQI Alert");
            when(def.getVersion()).thenReturn(2);
            when(def.getDeploymentId()).thenReturn("dep-1");
            when(def.getTenantId()).thenReturn("hcm");
            when(def.isSuspended()).thenReturn(false);

            var query = mock(org.camunda.bpm.engine.repository.ProcessDefinitionQuery.class);
            when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
            when(query.latestVersion()).thenReturn(query);
            when(query.list()).thenReturn(List.of(def));

            List<ProcessDefinitionDto> result = workflowService.listDefinitions();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("pd-1");
            assertThat(result.get(0).getKey()).isEqualTo("aiC01");
            assertThat(result.get(0).getTenantId()).isEqualTo("hcm");
            assertThat(result.get(0).isSuspended()).isFalse();
        }

        @Test
        @DisplayName("returns empty list when no definitions")
        void returnsEmpty() {
            var query = mock(org.camunda.bpm.engine.repository.ProcessDefinitionQuery.class);
            when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
            when(query.latestVersion()).thenReturn(query);
            when(query.list()).thenReturn(List.of());

            assertThat(workflowService.listDefinitions()).isEmpty();
        }
    }

    // ─── listInstances ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("listInstances")
    class ListInstances {

        PageRequest pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("ACTIVE status queries runtime service")
        void activeStatus() {
            ProcessInstance pi = mock(ProcessInstance.class);
            when(pi.getId()).thenReturn("pi-1");
            when(pi.getProcessDefinitionId()).thenReturn("pd-1:1");
            when(pi.getBusinessKey()).thenReturn("bk");

            var query = mock(org.camunda.bpm.engine.runtime.ProcessInstanceQuery.class);
            when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
            when(query.active()).thenReturn(query);
            when(query.orderByProcessInstanceId()).thenReturn(query);
            when(query.desc()).thenReturn(query);
            when(query.listPage(0, 10)).thenReturn(List.of(pi));
            when(query.count()).thenReturn(1L);

            when(runtimeService.getVariables("pi-1")).thenReturn(Map.of("scenarioKey", "aiC01"));

            Page<ProcessInstanceDto> page = workflowService.listInstances("ACTIVE", pageable);
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getState()).isEqualTo("ACTIVE");
            assertThat(page.getContent().get(0).getVariables()).containsEntry("scenarioKey", "aiC01");
        }

        @Test
        @DisplayName("COMPLETED status queries history service")
        void completedStatus() {
            HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
            when(hpi.getId()).thenReturn("pi-2");
            when(hpi.getProcessDefinitionId()).thenReturn("pd-2:1");
            when(hpi.getProcessDefinitionKey()).thenReturn("aiC02");
            when(hpi.getBusinessKey()).thenReturn(null);
            when(hpi.getStartTime()).thenReturn(new Date());
            when(hpi.getEndTime()).thenReturn(new Date());

            var query = mock(org.camunda.bpm.engine.history.HistoricProcessInstanceQuery.class);
            when(historyService.createHistoricProcessInstanceQuery()).thenReturn(query);
            when(query.finished()).thenReturn(query);
            when(query.orderByProcessInstanceStartTime()).thenReturn(query);
            when(query.desc()).thenReturn(query);
            when(query.listPage(0, 10)).thenReturn(List.of(hpi));
            when(query.count()).thenReturn(1L);

            Page<ProcessInstanceDto> page = workflowService.listInstances("COMPLETED", pageable);
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getState()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("ALL status returns combined history")
        void allStatus() {
            HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
            when(hpi.getId()).thenReturn("pi-3");
            when(hpi.getProcessDefinitionId()).thenReturn("pd-3:1");
            when(hpi.getProcessDefinitionKey()).thenReturn("aiC03");
            when(hpi.getBusinessKey()).thenReturn(null);
            when(hpi.getStartTime()).thenReturn(null);
            when(hpi.getEndTime()).thenReturn(null);

            var query = mock(org.camunda.bpm.engine.history.HistoricProcessInstanceQuery.class);
            when(historyService.createHistoricProcessInstanceQuery()).thenReturn(query);
            when(query.orderByProcessInstanceStartTime()).thenReturn(query);
            when(query.desc()).thenReturn(query);
            when(query.listPage(0, 10)).thenReturn(List.of(hpi));
            when(query.count()).thenReturn(1L);

            Page<ProcessInstanceDto> page = workflowService.listInstances(null, pageable);
            assertThat(page.getContent().get(0).getState()).isEqualTo("ACTIVE"); // endTime null → ACTIVE
        }
    }

    // ─── startProcess ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("startProcess")
    class StartProcess {

        @Test
        @DisplayName("returns ProcessInstanceDto on success")
        void success() {
            ProcessInstance mockInstance = mock(ProcessInstance.class);
            when(mockInstance.getId()).thenReturn("pi-12345");
            when(mockInstance.getProcessDefinitionId()).thenReturn("pd-aiC01:1:999");
            when(mockInstance.getBusinessKey()).thenReturn(null);
            when(runtimeService.startProcessInstanceByKey(eq("aiC01_aqiCitizenAlert"), anyMap()))
                .thenReturn(mockInstance);
            when(runtimeService.getVariables("pi-12345")).thenReturn(Map.of("scenarioKey", "aiC01"));

            ProcessInstanceDto result = workflowService.startProcess(
                "aiC01_aqiCitizenAlert", Map.of("scenarioKey", "aiC01"));

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("pi-12345");
            assertThat(result.getState()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("throws WorkflowNotFoundException for invalid process key")
        void invalidKey() {
            when(runtimeService.startProcessInstanceByKey(eq("nonexistent-process"), anyMap()))
                .thenThrow(new ProcessEngineException("no processes deployed with key 'nonexistent-process'"));

            assertThatThrownBy(() -> workflowService.startProcess("nonexistent-process", Map.of()))
                .isInstanceOf(WorkflowNotFoundException.class)
                .hasMessageContaining("nonexistent-process");
        }
    }

    // ─── hasActiveProcess ────────────────────────────────────────────────────

    @Nested
    @DisplayName("hasActiveProcess")
    class HasActiveProcess {

        @Test
        @DisplayName("returns true when active instance exists")
        void exists() {
            var query = mock(org.camunda.bpm.engine.runtime.ProcessInstanceQuery.class);
            when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
            when(query.processDefinitionKey("aiC01")).thenReturn(query);
            when(query.variableValueEquals("sensorId", "S-001")).thenReturn(query);
            when(query.active()).thenReturn(query);
            when(query.count()).thenReturn(1L);

            assertThat(workflowService.hasActiveProcess("aiC01", "sensorId", "S-001")).isTrue();
        }

        @Test
        @DisplayName("returns false when no active instance")
        void notExists() {
            var query = mock(org.camunda.bpm.engine.runtime.ProcessInstanceQuery.class);
            when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
            when(query.processDefinitionKey("aiC01")).thenReturn(query);
            when(query.variableValueEquals("sensorId", "S-001")).thenReturn(query);
            when(query.active()).thenReturn(query);
            when(query.count()).thenReturn(0L);

            assertThat(workflowService.hasActiveProcess("aiC01", "sensorId", "S-001")).isFalse();
        }
    }

    // ─── getInstanceVariables ────────────────────────────────────────────────

    @Nested
    @DisplayName("getInstanceVariables")
    class GetInstanceVariables {

        @Test
        @DisplayName("returns variables from runtime service")
        void fromRuntime() {
            when(runtimeService.getVariables("pi-1")).thenReturn(Map.of("key", "val"));

            Map<String, Object> vars = workflowService.getInstanceVariables("pi-1");
            assertThat(vars).containsEntry("key", "val");
        }

        @Test
        @DisplayName("falls back to history service on ProcessEngineException")
        void fallbackToHistory() {
            when(runtimeService.getVariables("pi-2")).thenThrow(new ProcessEngineException("not found"));

            HistoricVariableInstance var1 = mock(HistoricVariableInstance.class);
            when(var1.getName()).thenReturn("decision");
            when(var1.getValue()).thenReturn("ESCALATE");

            var query = mock(org.camunda.bpm.engine.history.HistoricVariableInstanceQuery.class);
            when(historyService.createHistoricVariableInstanceQuery()).thenReturn(query);
            when(query.processInstanceId("pi-2")).thenReturn(query);
            when(query.list()).thenReturn(List.of(var1));

            Map<String, Object> vars = workflowService.getInstanceVariables("pi-2");
            assertThat(vars).containsEntry("decision", "ESCALATE");
        }

        @Test
        @DisplayName("deduplicates via (a, b) -> b merge function")
        void deduplicatesVars() {
            when(runtimeService.getVariables("pi-3")).thenThrow(new ProcessEngineException("not found"));

            HistoricVariableInstance var1 = mock(HistoricVariableInstance.class);
            when(var1.getName()).thenReturn("key");
            when(var1.getValue()).thenReturn("old");

            HistoricVariableInstance var2 = mock(HistoricVariableInstance.class);
            when(var2.getName()).thenReturn("key");
            when(var2.getValue()).thenReturn("new");

            var query = mock(org.camunda.bpm.engine.history.HistoricVariableInstanceQuery.class);
            when(historyService.createHistoricVariableInstanceQuery()).thenReturn(query);
            when(query.processInstanceId("pi-3")).thenReturn(query);
            when(query.list()).thenReturn(List.of(var1, var2));

            Map<String, Object> vars = workflowService.getInstanceVariables("pi-3");
            assertThat(vars).containsEntry("key", "new");
        }
    }

    // ─── getProcessDefinitionXml ─────────────────────────────────────────────

    @Nested
    @DisplayName("getProcessDefinitionXml")
    class GetProcessDefinitionXml {

        @Test
        @DisplayName("returns BPMN XML for valid definition")
        void success() {
            ProcessDefinition def = mock(ProcessDefinition.class);
            when(def.getDeploymentId()).thenReturn("dep-1");
            when(def.getResourceName()).thenReturn("process.bpmn");
            when(repositoryService.getProcessDefinition("pd-1")).thenReturn(def);
            when(repositoryService.getResourceAsStream("dep-1", "process.bpmn"))
                .thenReturn(new ByteArrayInputStream("<xml/>".getBytes()));

            String xml = workflowService.getProcessDefinitionXml("pd-1");
            assertThat(xml).isEqualTo("<xml/>");
        }

        @Test
        @DisplayName("throws WorkflowNotFoundException for missing definition")
        void notFound() {
            when(repositoryService.getProcessDefinition("missing"))
                .thenThrow(new ProcessEngineException("not found"));

            assertThatThrownBy(() -> workflowService.getProcessDefinitionXml("missing"))
                .isInstanceOf(WorkflowNotFoundException.class);
        }
    }
}
