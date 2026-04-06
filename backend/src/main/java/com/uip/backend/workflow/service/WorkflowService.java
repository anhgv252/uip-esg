package com.uip.backend.workflow.service;

import com.uip.backend.workflow.dto.ProcessDefinitionDto;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.HistoryService;
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
                    .listPage((int) pageable.getOffset(), pageable.getPageSize());
            
            long total = runtimeService.createProcessInstanceQuery().active().count();
            
            List<ProcessInstanceDto> dtos = instances.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            
            return new PageImpl<>(dtos, pageable, total);
            
        } else if ("COMPLETED".equalsIgnoreCase(status)) {
            List<HistoricProcessInstance> instances = historyService.createHistoricProcessInstanceQuery()
                    .finished()
                    .listPage((int) pageable.getOffset(), pageable.getPageSize());
            
            long total = historyService.createHistoricProcessInstanceQuery().finished().count();
            
            List<ProcessInstanceDto> dtos = instances.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            
            return new PageImpl<>(dtos, pageable, total);
            
        } else {
            // All instances (active + completed)
            List<HistoricProcessInstance> instances = historyService.createHistoricProcessInstanceQuery()
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
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(processKey, variables);
        log.info("Process started with instance ID: {}", instance.getId());
        return toDto(instance);
    }

    public Map<String, Object> getInstanceVariables(String instanceId) {
        log.info("Fetching variables for process instance: {}", instanceId);
        return runtimeService.getVariables(instanceId);
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
        Map<String, Object> variables = runtimeService.getVariables(instance.getId());
        return ProcessInstanceDto.builder()
                .id(instance.getId())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionKey(instance.getProcessInstanceId())
                .businessKey(instance.getBusinessKey())
                .state("ACTIVE")
                .startTime(null) // Not available directly from RuntimeService
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
