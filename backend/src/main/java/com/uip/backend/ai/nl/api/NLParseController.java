package com.uip.backend.ai.nl.api;

import com.uip.backend.ai.nl.NLInferenceException;
import com.uip.backend.ai.nl.NLIntentParserService;
import com.uip.backend.ai.nl.validation.BpmnValidationException;
import com.uip.backend.ai.nl.domain.NLParseRequest;
import com.uip.backend.ai.nl.domain.NLParseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoint for Vietnamese NL→BPMN intent parsing.
 * 
 * <p>M5-2 T02: POST /api/v1/nl/parse with X-GDPR-Mode header.
 */
@RestController
@RequestMapping("/api/v1/nl")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI", description = "Natural language processing for workflow generation")
@SecurityRequirement(name = "Bearer Authentication")
public class NLParseController {

    private final NLIntentParserService parserService;

    @PostMapping("/parse")
    @Operation(summary = "Parse Vietnamese operator command to BPMN workflow intent")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Intent parsed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "422", description = "Generated BPMN failed validation"),
        @ApiResponse(responseCode = "503", description = "Model inference unavailable")
    })
    public ResponseEntity<NLParseResponse> parse(
            @RequestHeader(value = "X-GDPR-Mode", defaultValue = "true") boolean gdprMode,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NLParseRequestDto request) {

        String requestId = UUID.randomUUID().toString();

        // Prefer X-Tenant-ID header (inter-service), fall back to JWT claim (operator UI)
        String tenantId = (tenantIdHeader != null && !tenantIdHeader.isBlank())
                ? tenantIdHeader
                : (jwt != null ? (String) jwt.getClaim("tenant_id") : "default");
        if (tenantId == null || tenantId.isBlank()) tenantId = "default";

        log.info("NL parse request: gdprMode={}, tenantId={}, requestId={}",
                gdprMode, tenantId, requestId);

        try {
            NLParseRequest parseRequest = new NLParseRequest(
                request.text(),
                request.workflowContext(),
                gdprMode,
                tenantId,
                requestId
            );

            NLParseResult result = parserService.parse(parseRequest);

            NLParseResponse response = new NLParseResponse(
                result.intent(),
                result.confidence(),
                result.entities(),
                result.modelUsed().name().toLowerCase(),
                result.latencyMs(),
                result.bpmnTemplate()
            );
            
            log.info("NL parse success: intent={}, confidence={}, latencyMs={}, requestId={}",
                    result.intent(), result.confidence(), result.latencyMs(), requestId);
            
            return ResponseEntity.ok(response);
            
        } catch (BpmnValidationException e) {
            log.warn("BPMN validation rejected generated workflow: errors={}, requestId={}",
                    e.getResult().errors(), requestId);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new NLParseResponse(
                    "INVALID_BPMN",
                    0.0,
                    null,
                    "unknown",
                    0L,
                    null
                ));
        } catch (NLInferenceException e) {
            log.error("NL inference failed: requestId={}", requestId, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new NLParseResponse(
                    "ERROR",
                    0.0,
                    null,
                    "unknown",
                    0L,
                    null
                ));
        } catch (Exception e) {
            log.error("Unexpected error in NL parse: requestId={}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new NLParseResponse(
                    "ERROR",
                    0.0,
                    null,
                    "unknown",
                    0L,
                    null
                ));
        }
    }
}
