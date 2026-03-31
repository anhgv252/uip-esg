package com.uip.backend.alert.api;

import com.uip.backend.alert.api.dto.AlertRuleRequest;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/alert-rules")
@RequiredArgsConstructor
@Tag(name = "Admin — Alert Rules", description = "CRUD for alert rule configuration (ADMIN only)")
public class AlertRuleController {

    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "List all active alert rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlertRule>> listRules() {
        return ResponseEntity.ok(alertService.listRules());
    }

    @PostMapping
    @Operation(summary = "Create a new alert rule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AlertRule> createRule(@Valid @RequestBody AlertRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.createRule(req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate an alert rule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        alertService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
