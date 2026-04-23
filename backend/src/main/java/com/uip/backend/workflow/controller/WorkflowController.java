package com.uip.backend.workflow.controller;

import com.uip.backend.workflow.dto.ProcessDefinitionDto;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@Tag(name = "AI Workflows", description = "Camunda BPMN workflow management")
@SecurityRequirement(name = "Bearer Authentication")
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping("/definitions")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @Operation(summary = "List all process definitions", description = "Returns all deployed BPMN process definitions")
    public ResponseEntity<List<ProcessDefinitionDto>> listDefinitions() {
        return ResponseEntity.ok(workflowService.listDefinitions());
    }

    @GetMapping(value = "/definitions/{id}/xml", produces = "text/xml")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @Operation(summary = "Get BPMN XML", description = "Returns the BPMN XML for a specific process definition")
    public ResponseEntity<String> getDefinitionXml(@PathVariable String id) {
        return ResponseEntity.ok(workflowService.getProcessDefinitionXml(id));
    }

    @GetMapping("/instances")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @Operation(summary = "List process instances", description = "Returns paginated list of process instances filtered by status")
    public ResponseEntity<Page<ProcessInstanceDto>> listInstances(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(workflowService.listInstances(status, pageable));
    }

    @GetMapping("/instances/{id}/variables")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @Operation(summary = "Get instance variables", description = "Returns all variables for a specific process instance")
    public ResponseEntity<Map<String, Object>> getInstanceVariables(@PathVariable String id) {
        return ResponseEntity.ok(workflowService.getInstanceVariables(id));
    }

    @PostMapping("/start/{processKey}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Start a new process", description = "Starts a new instance of the specified process with given variables")
    public ResponseEntity<ProcessInstanceDto> startProcess(
            @PathVariable String processKey,
            @RequestBody Map<String, Object> variables) {
        return ResponseEntity.ok(workflowService.startProcess(processKey, variables));
    }
}
