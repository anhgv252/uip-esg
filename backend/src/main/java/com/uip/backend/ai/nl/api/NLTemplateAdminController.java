package com.uip.backend.ai.nl.api;

import com.uip.backend.ai.nl.BpmnTemplateLibrary;
import com.uip.backend.ai.nl.template.NlBpmnTemplate;
import com.uip.backend.ai.nl.template.NlBpmnTemplateRepository;
import com.uip.backend.ai.nl.validation.BpmnValidationService;
import com.uip.backend.ai.nl.validation.ValidationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin API for managing NL→BPMN templates at runtime.
 *
 * <p>Operators/admins can view, update, and validate BPMN templates
 * without redeployment. DB overrides take precedence over classpath.
 *
 * <p>Required scope: {@code workflow:write} + {@code ROLE_ADMIN} or {@code ROLE_OPERATOR}.
 */
@RestController
@RequestMapping("/api/v1/admin/nl/templates")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "NL Templates Admin", description = "Runtime management of BPMN workflow templates")
@SecurityRequirement(name = "Bearer Authentication")
public class NLTemplateAdminController {

    private final NlBpmnTemplateRepository repository;
    private final BpmnTemplateLibrary templateLibrary;
    private final BpmnValidationService validationService;

    // DTO records

    record TemplateListItem(String intent, String displayName, String description,
                             Integer version, Boolean isActive, String source) {}

    record TemplateDetail(String intent, String displayName, String description,
                           String bpmnXml, Integer version, Boolean isActive, String source) {}

    record UpsertTemplateRequest(
        @NotBlank String displayName,
        String description,
        @NotBlank @Size(max = 200_000) String bpmnXml
    ) {}

    record ValidateRequest(@NotBlank @Size(max = 200_000) String bpmnXml) {}

    record ValidateResponse(boolean valid, List<String> errors) {}

    // ---- Endpoints ----

    /**
     * List all templates — DB overrides + classpath fallbacks.
     * Source: "db" for DB overrides, "classpath" for fallback defaults.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @Operation(summary = "List all BPMN templates (DB overrides + classpath)")
    public ResponseEntity<List<TemplateListItem>> listTemplates() {
        var dbTemplates = repository.findAllByIsActiveTrueOrderByIntent();
        var dbByIntent = dbTemplates.stream()
            .collect(java.util.stream.Collectors.toMap(
                t -> t.getIntent() != null ? t.getIntent() : "",
                t -> t,
                (a, b) -> a
            ));

        List<TemplateListItem> items = BpmnTemplateLibrary.SUPPORTED_INTENTS.stream()
            .sorted()
            .map(intent -> {
                if (dbByIntent.containsKey(intent)) {
                    NlBpmnTemplate t = dbByIntent.get(intent);
                    return new TemplateListItem(intent, t.getDisplayName(), t.getDescription(),
                        t.getVersion(), t.getIsActive(), "db");
                } else {
                    boolean hasClasspath = templateLibrary.hasTemplate(intent);
                    return new TemplateListItem(intent, intent.replace('_', ' '), null,
                        1, hasClasspath, hasClasspath ? "classpath" : "missing");
                }
            })
            .toList();

        return ResponseEntity.ok(items);
    }

    /**
     * Get resolved template for intent (same resolution as runtime: DB first, then classpath).
     */
    @GetMapping("/{intent}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @Operation(summary = "Get BPMN template for intent")
    public ResponseEntity<TemplateDetail> getTemplate(@PathVariable String intent) {
        if (!BpmnTemplateLibrary.SUPPORTED_INTENTS.contains(intent)) {
            return ResponseEntity.notFound().build();
        }
        // Check DB first
        var dbOpt = repository.findByIntentAndIsActiveTrue(intent);
        if (dbOpt.isPresent()) {
            NlBpmnTemplate t = dbOpt.get();
            return ResponseEntity.ok(new TemplateDetail(intent, t.getDisplayName(),
                t.getDescription(), t.getBpmnXml(), t.getVersion(), t.getIsActive(), "db"));
        }
        // Classpath
        String xml = templateLibrary.getTemplate(intent);
        if (xml == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new TemplateDetail(intent, intent.replace('_', ' '),
            null, xml, 1, true, "classpath"));
    }

    /**
     * Create or update DB override template for an intent.
     * Validates BPMN before saving — returns 422 if invalid.
     */
    @PutMapping("/{intent}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Create or update BPMN template override for intent")
    public ResponseEntity<?> upsertTemplate(
            @PathVariable String intent,
            @Valid @RequestBody UpsertTemplateRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        if (!BpmnTemplateLibrary.SUPPORTED_INTENTS.contains(intent)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Unknown intent: " + intent,
                             "supported", BpmnTemplateLibrary.SUPPORTED_INTENTS));
        }

        // Validate BPMN before saving
        ValidationResult validation = validationService.validate(body.bpmnXml());
        if (!validation.valid()) {
            return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "BPMN validation failed", "errors", validation.errors()));
        }

        String updatedBy = jwt != null ? jwt.getSubject() : "admin";

        NlBpmnTemplate template = repository.findByIntentAndIsActiveTrue(intent)
            .orElseGet(() -> NlBpmnTemplate.builder().intent(intent).build());

        template.setDisplayName(body.displayName());
        template.setDescription(body.description());
        template.setBpmnXml(body.bpmnXml());
        template.setVersion(template.getVersion() == null ? 1 : template.getVersion() + 1);
        template.setIsActive(true);
        template.setUpdatedBy(updatedBy);
        if (template.getCreatedBy() == null) template.setCreatedBy(updatedBy);

        repository.save(template);

        log.info("BPMN template updated: intent={}, version={}, updatedBy={}", 
            intent, template.getVersion(), updatedBy);

        return ResponseEntity.ok(Map.of(
            "intent", intent,
            "version", template.getVersion(),
            "message", "Template saved and active. Next NL parse will use this template."
        ));
    }

    /**
     * Deactivate DB override for intent — reverts to classpath default.
     */
    @DeleteMapping("/{intent}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Deactivate DB override — revert to classpath default")
    public ResponseEntity<?> deactivateTemplate(@PathVariable String intent) {
        var dbOpt = repository.findByIntentAndIsActiveTrue(intent);
        if (dbOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No DB override active for intent: " + intent));
        }
        NlBpmnTemplate t = dbOpt.get();
        t.setIsActive(false);
        repository.save(t);
        log.info("BPMN template DB override deactivated: intent={}", intent);
        return ResponseEntity.ok(Map.of(
            "intent", intent,
            "message", "DB override deactivated. Classpath template will be used."
        ));
    }

    /**
     * Validate a BPMN XML snippet without saving.
     * Useful for operators to test their templates before applying.
     */
    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @Operation(summary = "Validate a BPMN XML template without saving")
    public ResponseEntity<ValidateResponse> validateTemplate(@Valid @RequestBody ValidateRequest body) {
        ValidationResult result = validationService.validate(body.bpmnXml());
        return ResponseEntity.status(result.valid() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ValidateResponse(result.valid(), result.errors()));
    }
}
