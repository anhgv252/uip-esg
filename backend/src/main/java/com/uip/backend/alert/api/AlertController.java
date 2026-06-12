package com.uip.backend.alert.api;

import com.uip.backend.alert.api.dto.AcknowledgeRequest;
import com.uip.backend.alert.api.dto.AlertEventDto;
import com.uip.backend.alert.api.dto.AlertFeedbackRequest;
import com.uip.backend.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Alert query and acknowledgement")
@SecurityRequirement(name = "Bearer Authentication")
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/notifications")
    @Operation(summary = "Recent HIGH/CRITICAL alerts for citizen notifications (last 48h)")
    @PreAuthorize("isAuthenticated()")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of alert notifications"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<Page<AlertEventDto>> getPublicNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.getPublicNotifications(page, size));
    }

    @GetMapping
    @Operation(summary = "Query alert events with optional filters")
    @PreAuthorize("isAuthenticated()")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of alert events"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<Page<AlertEventDto>> queryAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.queryAlerts(status, severity, module, from, to, page, size));
    }

    @PutMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge an alert")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alert acknowledged successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires OPERATOR or ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Alert not found")
    })
    public ResponseEntity<AlertEventDto> acknowledge(
            @PathVariable UUID id,
            @RequestBody(required = false) AcknowledgeRequest req,
            Authentication auth) {
        AcknowledgeRequest body = req != null ? req : new AcknowledgeRequest();
        return ResponseEntity.ok(alertService.acknowledgeAlert(id, auth.getName(), body));
    }

    @PutMapping("/{id}/escalate")
    @Operation(summary = "Escalate an alert to higher authority")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alert escalated successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires OPERATOR or ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Alert not found")
    })
    public ResponseEntity<AlertEventDto> escalate(
            @PathVariable UUID id,
            @RequestBody(required = false) AcknowledgeRequest req,
            Authentication auth) {
        String note = req != null ? req.getNote() : null;
        return ResponseEntity.ok(alertService.escalateAlert(id, auth.getName(), note));
    }

    @PutMapping("/{id}/resolve")
    @Operation(summary = "Resolve an escalated alert")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alert resolved successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires OPERATOR or ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Alert not found")
    })
    public ResponseEntity<AlertEventDto> resolve(
            @PathVariable UUID id,
            @RequestBody(required = false) AcknowledgeRequest req,
            Authentication auth) {
        String note = req != null ? req.getNote() : null;
        return ResponseEntity.ok(alertService.resolveAlert(id, auth.getName(), note));
    }

    @PostMapping("/{id}/feedback")
    @Operation(summary = "Submit operator feedback on an AI-generated alert (M4-COR-06)")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Feedback recorded, updated alert returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires OPERATOR or ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Alert not found")
    })
    public ResponseEntity<AlertEventDto> submitFeedback(
            @PathVariable UUID id,
            @RequestBody AlertFeedbackRequest feedback,
            Authentication auth) {
        AlertEventDto updated = alertService.recordFeedback(
                id, auth.getName(), feedback.getCorrect(), feedback.getComment());
        return ResponseEntity.ok(updated);
    }
}
