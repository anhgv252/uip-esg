package com.uip.backend.workflow.service;

import com.uip.backend.common.exception.WorkflowNotFoundException;
import com.uip.backend.workflow.dto.ProcessDefinitionDto;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public List<ProcessDefinitionDto> listDefinitions() {
        log.info("Fetching all process definitions");
        return repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .list()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Page<ProcessInstanceDto> listInstances(String status, Pageable pageable) {
        log.info("Fetching process instances with status: {}", status);
        
        if ("ACTIVE".equalsIgnoreCase(status)) {
            List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                    .active()
                    .orderByProcessInstanceId().desc()
                    .listPage((int) pageable.getOffset(), pageable.getPageSize());
            
            long total = runtimeService.createProcessInstanceQuery().active().count();
            
            List<ProcessInstanceDto> dtos = instances.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            
            return new PageImpl<>(dtos, pageable, total);
            
        } else if ("COMPLETED".equalsIgnoreCase(status)) {
            List<HistoricProcessInstance> instances = historyService.createHistoricProcessInstanceQuery()
                    .finished()
                    .orderByProcessInstanceStartTime().desc()
                    .listPage((int) pageable.getOffset(), pageable.getPageSize());
            
            long total = historyService.createHistoricProcessInstanceQuery().finished().count();
            
            List<ProcessInstanceDto> dtos = instances.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            
            return new PageImpl<>(dtos, pageable, total);
            
        } else {
            // All instances (active + completed)
            List<HistoricProcessInstance> instances = historyService.createHistoricProcessInstanceQuery()
                    .orderByProcessInstanceStartTime().desc()
                    .listPage((int) pageable.getOffset(), pageable.getPageSize());
            
            long total = historyService.createHistoricProcessInstanceQuery().count();
            
            List<ProcessInstanceDto> dtos = instances.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            
            return new PageImpl<>(dtos, pageable, total);
        }
    }

    public ProcessInstanceDto startProcess(String processKey, Map<String, Object> variables) {
        log.info("Starting process: {} with variables: {}", processKey, variables.keySet());
        try {
            ProcessInstance instance = runtimeService.startProcessInstanceByKey(processKey, variables);
            log.info("Process started with instance ID: {}", instance.getId());
            return toDto(instance);
        } catch (ProcessEngineException e) {
            throw new WorkflowNotFoundException("Process not found: " + processKey);
        }
    }

    public boolean hasActiveProcess(String processKey, String variableName, String variableValue) {
        return runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processKey)
                .variableValueEquals(variableName, variableValue)
                .active()
                .count() > 0;
    }

    public Map<String, Object> getInstanceVariables(String instanceId) {
        log.info("Fetching variables for process instance: {}", instanceId);
        try {
            return runtimeService.getVariables(instanceId);
        } catch (ProcessEngineException e) {
            log.debug("Instance {} not in runtime, checking history", instanceId);
            List<org.camunda.bpm.engine.history.HistoricVariableInstance> vars =
                    historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(instanceId)
                            .list();
            return vars.stream().collect(Collectors.toMap(
                    org.camunda.bpm.engine.history.HistoricVariableInstance::getName,
                    org.camunda.bpm.engine.history.HistoricVariableInstance::getValue,
                    (a, b) -> b
            ));
        }
    }

    public String getProcessDefinitionXml(String definitionId) {
        log.info("Fetching BPMN XML for definition: {}", definitionId);
        try {
            org.camunda.bpm.engine.repository.ProcessDefinition definition =
                    repositoryService.getProcessDefinition(definitionId);
            java.io.InputStream is = repositoryService.getResourceAsStream(
                    definition.getDeploymentId(), definition.getResourceName());
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new WorkflowNotFoundException("Process definition not found: " + definitionId);
        }
    }

    private ProcessDefinitionDto toDto(ProcessDefinition def) {
        return ProcessDefinitionDto.builder()
                .id(def.getId())
                .key(def.getKey())
                .name(def.getName())
                .version(def.getVersion())
                .deploymentId(def.getDeploymentId())
                .tenantId(def.getTenantId())
                .suspended(def.isSuspended())
                .build();
    }

    private ProcessInstanceDto toDto(ProcessInstance instance) {
        Map<String, Object> variables = Map.of();
        try {
            variables = runtimeService.getVariables(instance.getId());
        } catch (ProcessEngineException e) {
            // Process completed synchronously — execution no longer in runtime
        }
        LocalDateTime startTime = null;
        try {
            HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instance.getId())
                    .singleResult();
            if (hpi != null && hpi.getStartTime() != null) {
                startTime = LocalDateTime.ofInstant(hpi.getStartTime().toInstant(), ZoneId.systemDefault());
            }
        } catch (Exception ignored) {}

        String processDefinitionKey = null;
        try {
            ProcessDefinition def = repositoryService.getProcessDefinition(instance.getProcessDefinitionId());
            processDefinitionKey = def.getKey();
        } catch (Exception ignored) {}

        return ProcessInstanceDto.builder()
                .id(instance.getId())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionKey(processDefinitionKey)
                .businessKey(instance.getBusinessKey())
                .state("ACTIVE")
                .startTime(startTime)
                .variables(variables)
                .build();
    }

    private ProcessInstanceDto toDto(HistoricProcessInstance instance) {
        LocalDateTime startTime = instance.getStartTime() != null
                ? LocalDateTime.ofInstant(instance.getStartTime().toInstant(), ZoneId.systemDefault())
                : null;
        
        String state = instance.getEndTime() != null ? "COMPLETED" : "ACTIVE";
        
        return ProcessInstanceDto.builder()
                .id(instance.getId())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionKey(instance.getProcessDefinitionKey())
                .businessKey(instance.getBusinessKey())
                .state(state)
                .startTime(startTime)
                .variables(Map.of()) // Variables need separate query for historic instances
                .build();
    }
}
