package com.uip.backend.alert.api;

import com.uip.backend.alert.api.dto.AcknowledgeRequest;
import com.uip.backend.alert.api.dto.AlertEventDto;
import com.uip.backend.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
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
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/notifications")
    @Operation(summary = "Recent HIGH/CRITICAL alerts for citizen notifications (last 48h)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<AlertEventDto>> getPublicNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.getPublicNotifications(page, size));
    }

    @GetMapping
    @Operation(summary = "Query alert events with optional filters")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<AlertEventDto>> queryAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.queryAlerts(status, severity, from, to, page, size));
    }

    @PutMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge an alert")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
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
    public ResponseEntity<AlertEventDto> escalate(
            @PathVariable UUID id,
            @RequestBody(required = false) AcknowledgeRequest req,
            Authentication auth) {
        String note = req != null ? req.getNote() : null;
        return ResponseEntity.ok(alertService.escalateAlert(id, auth.getName(), note));
    }
}
