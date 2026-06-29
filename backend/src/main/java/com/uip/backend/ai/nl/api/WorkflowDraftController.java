package com.uip.backend.ai.nl.api;

import com.uip.backend.ai.nl.BpmnSimulatorService;
import com.uip.backend.ai.nl.BpmnSynthesisService;
import com.uip.backend.ai.nl.NLIntentParserService;
import com.uip.backend.ai.nl.domain.*;
import com.uip.backend.ai.nl.repository.WorkflowDraftRepository;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for AI-generated workflow drafts (BPMN synthesis + review).
 *
 * <p>M5-3 T01: Operator review workflow for synthesised BPMN.
 * <p>M5-3 T04: BPMN simulation for dry-run testing.
 */
@RestController
@RequestMapping("/api/v1/nl")
@Tag(name = "Workflow Drafts", description = "AI-generated BPMN workflow synthesis and operator review")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Slf4j
public class WorkflowDraftController {

    private final BpmnSynthesisService synthesisService;
    private final BpmnSimulatorService simulatorService;
    private final NLIntentParserService parserService;
    private final WorkflowDraftRepository draftRepository;

    /**
     * POST /api/v1/nl/parse/synthesise — Synthesise BPMN from NL input.
     *
     * <p>Workflow: NL parse → template loading → entity substitution → BR-010 injection → validation → persist.
     */
    @PostMapping("/parse/synthesise")
    @Operation(summary = "Synthesise BPMN workflow from natural language input",
               description = "Parse Vietnamese NL intent, generate production-ready BPMN with operator review gate, and save as draft")
    public ResponseEntity<WorkflowDraft> synthesise(
            @Valid @RequestBody SynthesiseRequest request,
            @AuthenticationPrincipal UserDetails user) {

        String tenantId = TenantContext.getCurrentTenant();
        String requestedBy = user.getUsername();

        log.info("POST /api/v1/nl/parse/synthesise: text='{}', tenant={}, user={}", 
                 request.text(), tenantId, requestedBy);

        // Step 1: Parse NL intent
        NLParseRequest parseRequest = new NLParseRequest(
            request.text(),
            null, // workflowContext
            true, // gdprMode
            tenantId,
            java.util.UUID.randomUUID().toString() // requestId
        );
        NLParseResult parseResult = parserService.parse(parseRequest);

        // Step 2: Synthesise BPMN
        WorkflowDraft draft = synthesisService.synthesise(parseResult, tenantId, requestedBy);

        return ResponseEntity.ok(draft);
    }

    /**
     * GET /api/v1/nl/drafts — List workflow drafts (PENDING_REVIEW first).
     */
    @GetMapping("/drafts")
    @Operation(summary = "List workflow drafts", 
               description = "Retrieve all workflow drafts for current tenant, ordered by status (PENDING_REVIEW first) and creation time")
    public ResponseEntity<List<WorkflowDraft>> listDrafts(
            @RequestParam(required = false) DraftStatus status) {

        String tenantId = TenantContext.getCurrentTenant();
        log.info("GET /api/v1/nl/drafts: tenant={}, status={}", tenantId, status);

        List<WorkflowDraft> drafts;
        if (status != null) {
            drafts = draftRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        } else {
            drafts = draftRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }

        return ResponseEntity.ok(drafts);
    }

    /**
     * GET /api/v1/nl/drafts/{id} — Get single workflow draft.
     */
    @GetMapping("/drafts/{id}")
    @Operation(summary = "Get workflow draft by ID", 
               description = "Retrieve single workflow draft with full BPMN XML and metadata")
    public ResponseEntity<WorkflowDraft> getDraft(@PathVariable UUID id) {

        String tenantId = TenantContext.getCurrentTenant();
        log.info("GET /api/v1/nl/drafts/{}: tenant={}", id, tenantId);

        WorkflowDraft draft = draftRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        return ResponseEntity.ok(draft);
    }

    /**
     * PUT /api/v1/nl/drafts/{id}/approve — Approve workflow (ADMIN/OPERATOR only).
     */
    @PutMapping("/drafts/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Approve workflow draft", 
               description = "Mark draft as APPROVED — requires ADMIN or OPERATOR role")
    public ResponseEntity<WorkflowDraft> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {

        String tenantId = TenantContext.getCurrentTenant();
        String approvedBy = user.getUsername();

        log.info("PUT /api/v1/nl/drafts/{}/approve: tenant={}, approvedBy={}", id, tenantId, approvedBy);

        WorkflowDraft draft = draftRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        if (draft.getStatus() != DraftStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Can only approve drafts in PENDING_REVIEW status, current: " + draft.getStatus());
        }

        draft.setStatus(DraftStatus.APPROVED);
        draft.setApprovedBy(approvedBy);
        WorkflowDraft updated = draftRepository.save(draft);

        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/v1/nl/drafts/{id}/reject — Reject workflow.
     */
    @PutMapping("/drafts/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Reject workflow draft", 
               description = "Mark draft as REJECTED with reason — requires ADMIN or OPERATOR role")
    public ResponseEntity<WorkflowDraft> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectRequest request) {

        String tenantId = TenantContext.getCurrentTenant();
        log.info("PUT /api/v1/nl/drafts/{}/reject: tenant={}, reason='{}'", id, tenantId, request.reason());

        WorkflowDraft draft = draftRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        if (draft.getStatus() != DraftStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Can only reject drafts in PENDING_REVIEW status, current: " + draft.getStatus());
        }

        draft.setStatus(DraftStatus.REJECTED);
        draft.setRejectionReason(request.reason());
        WorkflowDraft updated = draftRepository.save(draft);

        return ResponseEntity.ok(updated);
    }

    /**
     * POST /api/v1/nl/drafts/{id}/simulate — Simulate workflow (dry-run test).
     */
    @PostMapping("/drafts/{id}/simulate")
    @Operation(summary = "Simulate workflow draft", 
               description = "Run dry-run BPMN simulation without real actuation — updates status to SIMULATED if successful")
    public ResponseEntity<SimulationResult> simulate(@PathVariable UUID id) {

        String tenantId = TenantContext.getCurrentTenant();
        log.info("POST /api/v1/nl/drafts/{}/simulate: tenant={}", id, tenantId);

        WorkflowDraft draft = draftRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + id));

        SimulationResult result = simulatorService.simulate(draft.getIntent(), draft.getBpmnXml());

        // Update status to SIMULATED if successful
        if (result.success()) {
            draft.setStatus(DraftStatus.SIMULATED);
            draftRepository.save(draft);
            log.info("Draft {} simulated successfully, status updated to SIMULATED", id);
        } else {
            log.warn("Draft {} simulation failed, status not updated", id);
        }

        return ResponseEntity.ok(result);
    }

    // ========== DTOs ==========

    public record SynthesiseRequest(@NotBlank String text) {}

    public record RejectRequest(@NotBlank String reason) {}
}
