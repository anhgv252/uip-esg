package com.uip.backend.bms.api;

import com.uip.backend.bms.domain.CommandStatus;
import com.uip.backend.bms.domain.PendingBmsCommand;
import com.uip.backend.bms.service.BmsCommandService;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the BMS 2-step command confirmation flow (M4-COR-03).
 *
 * <h3>BR-010 enforcement</h3>
 * <p>Propose is restricted to ADMIN (system/AI-generated proposals).
 * Approve/Reject require OPERATOR or ADMIN — only human operators may authorize actuator commands.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bms/commands")
@RequiredArgsConstructor
@Tag(name = "BMS Commands", description = "BMS 2-step command confirmation flow (BR-010 safety)")
@SecurityRequirement(name = "Bearer Authentication")
public class BmsCommandController {

    private final BmsCommandService commandService;

    // ── List pending ─────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List PENDING BMS commands awaiting operator approval")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned"),
            @ApiResponse(responseCode = "403", description = "Tenant context required")
    })
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<List<PendingBmsCommand>> listPending() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<PendingBmsCommand> pending = commandService.getPendingCommandsForTenant(tenantId);
        return ResponseEntity.ok(pending);
    }

    // ── Propose ───────────────────────────────────────────────────────────────

    @PostMapping("/propose")
    @Operation(summary = "Propose a new BMS command (ADMIN/system only)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Command proposed, awaiting approval"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "403", description = "ADMIN role required or no tenant context")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PendingBmsCommand> propose(@RequestBody ProposeRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        PendingBmsCommand cmd = commandService.proposeCommand(
                request.buildingId(),
                tenantId,
                request.commandType(),
                request.targetDevice(),
                request.targetValue(),
                request.requestedBy()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(cmd);
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending BMS command — triggers physical actuator (BR-010)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Command approved and executed"),
            @ApiResponse(responseCode = "400", description = "Command not in PENDING state or expired"),
            @ApiResponse(responseCode = "403", description = "OPERATOR or ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<PendingBmsCommand> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        String operator = user != null ? user.getUsername() : "unknown";
        PendingBmsCommand cmd = commandService.approveCommand(id, operator);
        return ResponseEntity.ok(cmd);
    }

    // ── Reject ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending BMS command — no actuator action taken")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Command rejected"),
            @ApiResponse(responseCode = "400", description = "Command not in PENDING state"),
            @ApiResponse(responseCode = "403", description = "OPERATOR or ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Command not found")
    })
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<PendingBmsCommand> reject(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        String operator = user != null ? user.getUsername() : "unknown";
        PendingBmsCommand cmd = commandService.rejectCommand(id, operator);
        return ResponseEntity.ok(cmd);
    }

    // ── Request DTO ───────────────────────────────────────────────────────────

    /**
     * Request body for proposing a new BMS command.
     */
    public record ProposeRequest(
            @NotBlank String buildingId,
            @NotBlank @Size(max = 50) String commandType,
            @NotBlank @Size(max = 200) String targetDevice,
            @NotBlank @Size(max = 200) String targetValue,
            String requestedBy
    ) {}
}
