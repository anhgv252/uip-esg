package com.uip.backend.ai.nl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.ai.nl.domain.DraftStatus;
import com.uip.backend.ai.nl.domain.NLParseResult;
import com.uip.backend.ai.nl.domain.WorkflowDraft;
import com.uip.backend.ai.nl.repository.WorkflowDraftRepository;
import com.uip.backend.ai.nl.validation.BpmnValidationException;
import com.uip.backend.ai.nl.validation.BpmnValidationService;
import com.uip.backend.ai.nl.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BPMN synthesis service — converts NL parse results into production-ready BPMN drafts.
 *
 * <p>M5-3 T01: Template + entity substitution + BR-010 gate injection + validation + persistence.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Load BPMN template for parsed intent (via BpmnTemplateLibrary)</li>
 *   <li>Substitute {{placeholder}} entities with extracted values</li>
 *   <li>Inject BR-010 operator review task if not present</li>
 *   <li>Validate via BpmnValidationService (XSD + custom rules)</li>
 *   <li>Persist as WorkflowDraft with status=PENDING_REVIEW</li>
 * </ol>
 *
 * <p>Non-negotiables:
 * <ul>
 *   <li>Always inject BR-010 review task (operator approval gate)</li>
 *   <li>BPMN must pass validation before persistence</li>
 *   <li>Never deploy directly — all workflows require operator approval</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BpmnSynthesisService {

    private final BpmnTemplateLibrary templateLibrary;
    private final BpmnValidationService validationService;
    private final WorkflowDraftRepository draftRepository;
    private final ObjectMapper objectMapper;

    private static final String BR010_REVIEW_TASK = """
        <userTask id="br010OperatorReview" name="BR-010 Operator Review">
          <documentation>Operator must review and approve this AI-generated workflow before execution.</documentation>
        </userTask>""";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-zA-Z_]+)}}");

    /**
     * Synthesise production-ready BPMN from NL parse result.
     *
     * @param parseResult NL parser output with intent, entities, confidence
     * @param tenantId    Tenant for multi-tenancy isolation
     * @param requestedBy User who requested workflow generation
     * @return Persisted workflow draft with status=PENDING_REVIEW
     * @throws IllegalArgumentException if template not found or BPMN validation fails
     */
    @Transactional
    public WorkflowDraft synthesise(NLParseResult parseResult, String tenantId, String requestedBy) {
        log.info("Synthesising BPMN for intent={}, tenant={}, requestedBy={}", 
                 parseResult.intent(), tenantId, requestedBy);

        // Step 1: Load template
        String template = templateLibrary.getTemplate(parseResult.intent());
        if (template == null) {
            throw new IllegalArgumentException("No BPMN template found for intent: " + parseResult.intent());
        }

        // Step 2: Substitute entities
        String bpmnXml = substituteEntities(template, parseResult.entities());
        log.debug("Entity substitution completed for intent={}", parseResult.intent());

        // Step 3: Inject BR-010 operator review gate if not present
        if (!bpmnXml.contains("br010OperatorReview")) {
            bpmnXml = injectReviewTask(bpmnXml);
            log.debug("Injected BR-010 operator review task for intent={}", parseResult.intent());
        } else {
            log.debug("BR-010 review task already present in template for intent={}", parseResult.intent());
        }

        // Step 4: Validate BPMN
        try {
            ValidationResult validationResult = validationService.validate(bpmnXml);
            if (!validationResult.valid()) {
                String errors = String.join("; ", validationResult.errors());
                log.error("BPMN validation failed for intent={}: {}", parseResult.intent(), errors);
                throw new IllegalArgumentException("BPMN validation failed: " + errors);
            }
        } catch (BpmnValidationException e) {
            log.error("BPMN validation exception for intent={}: {}", parseResult.intent(), e.getMessage());
            throw new IllegalArgumentException("BPMN validation failed: " + e.getMessage(), e);
        }

        // Step 5: Persist as draft
        String entitiesJson = null;
        try {
            if (parseResult.entities() != null && !parseResult.entities().isEmpty()) {
                entitiesJson = objectMapper.writeValueAsString(parseResult.entities());
            }
        } catch (Exception e) {
            log.warn("Failed to serialize entities to JSON: {}", e.getMessage());
        }

        WorkflowDraft draft = WorkflowDraft.builder()
            .tenantId(tenantId)
            .intent(parseResult.intent())
            .bpmnXml(bpmnXml)
            .confidence(parseResult.confidence())
            .status(DraftStatus.PENDING_REVIEW)
            .requestedBy(requestedBy)
            .extractedEntities(entitiesJson)
            .version(1)
            .nlParseLatencyMs(parseResult.latencyMs())
            .build();

        WorkflowDraft saved = draftRepository.save(draft);
        log.info("Workflow draft created: id={}, intent={}, tenant={}", 
                 saved.getId(), saved.getIntent(), saved.getTenantId());

        return saved;
    }

    /**
     * Substitute {{placeholder}} with extracted entity values.
     * Unknown placeholders are replaced with empty string (safe default).
     */
    private String substituteEntities(String template, Map<String, String> entities) {
        if (entities == null || entities.isEmpty()) {
            // No entities to substitute — replace all placeholders with empty string
            return PLACEHOLDER_PATTERN.matcher(template).replaceAll("");
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = entities.getOrDefault(key, ""); // Safe default: empty string
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Inject BR-010 operator review task before the first end event.
     * Ensures all workflows require operator approval before execution.
     */
    private String injectReviewTask(String bpmnXml) {
        // Find first endEvent and inject review task before it
        int endEventIndex = bpmnXml.indexOf("<endEvent");
        if (endEventIndex == -1) {
            log.warn("No endEvent found in BPMN — appending BR-010 review task at end of process");
            int processEndIndex = bpmnXml.lastIndexOf("</process>");
            if (processEndIndex != -1) {
                return bpmnXml.substring(0, processEndIndex) + BR010_REVIEW_TASK + bpmnXml.substring(processEndIndex);
            }
            return bpmnXml; // Fallback: no injection if no process element
        }

        return bpmnXml.substring(0, endEventIndex) + BR010_REVIEW_TASK + bpmnXml.substring(endEventIndex);
    }
}
