package com.uip.backend.aiworkflow.controller;

import com.uip.backend.aiworkflow.dto.WorkflowSummaryDto;
import com.uip.backend.aiworkflow.model.WorkflowDefinition;
import com.uip.backend.aiworkflow.service.WorkflowDefinitionService;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST API for AI Workflow Designer — WorkflowDefinition CRUD.
 *
 * Endpoints:
 *   POST   /api/v1/workflows              — create
 *   GET    /api/v1/workflows              — list (paginated)
 *   GET    /api/v1/workflows/{id}         — get by ID
 *   PUT    /api/v1/workflows/{id}         — update
 *   DELETE /api/v1/workflows/{id}         — soft delete
 *   POST   /api/v1/workflows/{id}/deploy  — deploy to Camunda
 *   POST   /api/v1/workflows/{id}/execute — start process instance
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Tag(name = "AI Workflow Designer", description = "BPMN workflow definition CRUD + deploy + execute")
@SecurityRequirement(name = "Bearer Authentication")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Create workflow definition")
    public ResponseEntity<WorkflowDefinition> create(@RequestBody CreateWorkflowRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        WorkflowDefinition created = service.create(
                tenantId, request.name(), request.description(), request.bpmnXml());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "List workflow definitions (paginated, without bpmnXml)")
    public ResponseEntity<Page<WorkflowSummaryDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.getCurrentTenant();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(service.list(tenantId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get workflow definition by ID")
    public ResponseEntity<WorkflowDefinition> getById(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(service.getById(id, tenantId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Update workflow definition")
    public ResponseEntity<WorkflowDefinition> update(
            @PathVariable UUID id,
            @RequestBody UpdateWorkflowRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(service.update(
                id, tenantId, request.name(), request.description(), request.bpmnXml()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft-delete workflow definition")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        service.delete(id, tenantId);
    }

    @PostMapping("/{id}/deploy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deploy workflow to Camunda engine")
    public ResponseEntity<WorkflowDefinition> deploy(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(service.deploy(id, tenantId));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Execute (start) a deployed workflow")
    public ResponseEntity<Map<String, Object>> execute(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(service.execute(id, tenantId));
    }

    // --- DTO records ---

    public record CreateWorkflowRequest(String name, String description, String bpmnXml) {}
    public record UpdateWorkflowRequest(String name, String description, String bpmnXml) {}
}
