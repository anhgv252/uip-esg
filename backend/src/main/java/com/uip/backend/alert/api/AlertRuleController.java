package com.uip.backend.alert.api;

import com.uip.backend.alert.api.dto.AlertRuleRequest;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = "Bearer Authentication")
public class AlertRuleController {

    private final AlertService alertService;

    @GetMapping
    @Operation(summary = "List all active alert rules")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of alert rules"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden — requires ADMIN role")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlertRule>> listRules() {
        return ResponseEntity.ok(alertService.listRules());
    }

    @PostMapping
    @Operation(summary = "Create a new alert rule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Alert rule created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden — requires ADMIN role")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AlertRule> createRule(@Valid @RequestBody AlertRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.createRule(req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate an alert rule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Alert rule deactivated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden — requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Alert rule not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        alertService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
