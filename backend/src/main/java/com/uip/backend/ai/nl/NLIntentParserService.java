package com.uip.backend.ai.nl;

import com.uip.backend.ai.nl.domain.NLParseRequest;
import com.uip.backend.ai.nl.domain.NLParseResult;
import com.uip.backend.ai.nl.domain.RoutingDecision;
import com.uip.backend.ai.nl.validation.BpmnValidationException;
import com.uip.backend.ai.nl.validation.BpmnValidationService;
import com.uip.backend.ai.nl.validation.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Main service for Vietnamese NL→BPMN intent parsing.
 *
 * <p>Orchestrates ModelRouter + parser selection (Claude or local ViT5).
 * Falls back to LocalNLParser when Claude API key is absent or unavailable.
 *
 * <p>M5-2 T02: 10 MVP4 workflow intent classification.
 */
@Service
@Slf4j
public class NLIntentParserService {

    private final ModelRouter modelRouter;
    private final ClaudeNLParser claudeParser;
    private final BpmnTemplateLibrary templateLibrary;
    private final BpmnValidationService bpmnValidationService;

    /** Optional — null if ai.nl.local-parser.enabled=false (real ViT5 available). */
    @Nullable
    private final LocalNLParser localParser;

    @Autowired
    public NLIntentParserService(ModelRouter modelRouter,
                                 ClaudeNLParser claudeParser,
                                 BpmnTemplateLibrary templateLibrary,
                                 BpmnValidationService bpmnValidationService,
                                 @Autowired(required = false) LocalNLParser localParser) {
        this.modelRouter = modelRouter;
        this.claudeParser = claudeParser;
        this.templateLibrary = templateLibrary;
        this.bpmnValidationService = bpmnValidationService;
        this.localParser = localParser;
    }

    /**
     * Parse Vietnamese operator text → BPMN intent + entities.
     * 
     * @param request NL parse request with gdpr_mode and text
     * @return parse result with intent, confidence, entities, model used
     * @throws NLInferenceException if model inference fails (timeout, 503)
     */
    public NLParseResult parse(NLParseRequest request) {
        long startMs = System.currentTimeMillis();
        
        // Route to LOCAL or CLOUD
        RoutingDecision routing = modelRouter.route(request);
        log.info("NL parse routing decision: route={}, reason={}, scanMs={}, requestId={}", 
                routing.route(), routing.reason(), routing.piiScanMs(), request.requestId());
        
        // Dispatch to appropriate parser
        NLParseResult baseResult = switch (routing.route()) {
            case CLOUD -> {
                log.debug("Calling Claude API parser, requestId={}", request.requestId());
                try {
                    yield claudeParser.parse(request.text(), request.requestId());
                } catch (NLInferenceException e) {
                    // Claude unavailable (no API key, timeout) — fallback to local if available
                    if (localParser != null) {
                        log.warn("Claude API unavailable, falling back to local parser. reason={}, requestId={}",
                                e.getMessage(), request.requestId());
                        yield localParser.parse(request.text(), request.requestId());
                    }
                    throw e; // no fallback available
                }
            }
            case LOCAL -> {
                log.debug("Calling local parser (ViT5/keyword stub), requestId={}", request.requestId());
                if (localParser == null) {
                    throw new NLInferenceException(
                        "Local parser not available (ai.nl.local-parser.enabled=false) and route=LOCAL", false);
                }
                yield localParser.parse(request.text(), request.requestId());
            }
        };
        
        // T03: Ground to BPMN template if intent is known
        String bpmnXml = null;
        if (!baseResult.intent().equals("UNKNOWN")) {
            try {
                Map<String, String> entitiesOrEmpty = baseResult.entities() != null ? 
                    baseResult.entities() : Map.of();
                bpmnXml = templateLibrary.instantiate(baseResult.intent(), entitiesOrEmpty);
                log.debug("BPMN template grounded for intent={}, requestId={}",
                         baseResult.intent(), request.requestId());
                // M5-2 T10: Fail-before-review-UI — validate immediately after generation
                ValidationResult validationResult = bpmnValidationService.validate(bpmnXml);
                if (!validationResult.valid()) {
                    log.warn("BPMN validation failed for intent={}, errors={}, requestId={}",
                            baseResult.intent(), validationResult.errors(), request.requestId());
                    throw new BpmnValidationException(validationResult);
                }
            } catch (Exception e) {
                log.warn("Failed to instantiate BPMN template for intent={}, requestId={}", 
                        baseResult.intent(), request.requestId(), e);
                // Continue without BPMN (return intent/entities only)
            }
        }
        
        long totalMs = System.currentTimeMillis() - startMs;
        
        // Return result with total latency + BPMN template
        return new NLParseResult(
            baseResult.intent(),
            baseResult.confidence(),
            baseResult.entities(),
            routing.route(),
            totalMs,
            bpmnXml
        );
    }
}
