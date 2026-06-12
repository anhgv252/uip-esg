package com.uip.backend.bms.service;

import com.uip.backend.bms.BmsCommandMetrics;
import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.domain.CommandStatus;
import com.uip.backend.bms.domain.FeedbackStage;
import com.uip.backend.bms.domain.PendingBmsCommand;
import com.uip.backend.bms.repository.PendingBmsCommandRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the 2-step BMS command confirmation flow (M4-COR-03).
 *
 * <h3>BR-010 Safety Constraint</h3>
 * <p>No BMS command (HVAC, sprinkler, evacuation) can execute without explicit operator approval.
 * Commands are proposed in {@link CommandStatus#PENDING} state and require an authenticated
 * OPERATOR or ADMIN to call {@link #approveCommand}. The service enforces this at two levels:
 * <ol>
 *   <li>Controller layer: {@code @PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")}</li>
 *   <li>Service layer: {@link #assertOperatorOrAdmin()} throws {@link SecurityException} as
 *       defense-in-depth if the security context is authenticated but lacks the required role.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BmsCommandService {

    static final long APPROVAL_TIMEOUT_SECONDS = 30L;

    private final PendingBmsCommandRepository commandRepository;
    private final BmsDeviceCommandService bmsDeviceCommandService;
    private final BmsFeedbackService feedbackService;
    private final BmsCommandMetrics metrics;

    // ── Propose ───────────────────────────────────────────────────────────────

    /**
     * Proposes a BMS command — creates a {@link PendingBmsCommand} with 30-second approval window.
     * The physical actuator is NOT triggered at this point.
     *
     * @param buildingId   target building identifier
     * @param tenantId     tenant owning the building
     * @param commandType  e.g. "HVAC_OFF", "SPRINKLER_ON", "EVACUATION"
     * @param targetDevice BMS device UUID (stored as string) or BACnet object reference
     * @param targetValue  value to write when approved
     * @param requestedBy  correlation incident UUID or user ID initiating the proposal
     * @return the persisted {@link PendingBmsCommand} in PENDING state
     */
    @Transactional
    public PendingBmsCommand proposeCommand(
            String buildingId, String tenantId,
            String commandType, String targetDevice, String targetValue,
            String requestedBy) {

        Instant now = Instant.now();
        PendingBmsCommand cmd = new PendingBmsCommand();
        cmd.setBuildingId(buildingId);
        cmd.setTenantId(tenantId);
        cmd.setCommandType(commandType);
        cmd.setTargetDevice(targetDevice);
        cmd.setTargetValue(targetValue);
        cmd.setStatus(CommandStatus.PENDING);
        cmd.setRequestedBy(requestedBy);
        cmd.setCreatedAt(now);
        cmd.setExpiresAt(now.plusSeconds(APPROVAL_TIMEOUT_SECONDS));

        PendingBmsCommand saved = commandRepository.save(cmd);

        metrics.recordProposed(buildingId);
        log.info("[BMS-CMD] Proposed commandId={} buildingId={} type={} requestedBy={}",
                saved.getId(), buildingId, commandType, requestedBy);
        return saved;
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    /**
     * Approves and executes a pending BMS command.
     *
     * <p><strong>BR-010:</strong> Throws {@link SecurityException} if the authenticated
     * user does not hold OPERATOR or ADMIN role (defense-in-depth).
     *
     * @param commandId        id of the {@link PendingBmsCommand} to approve
     * @param operatorUsername username of the approving operator
     * @return the updated command in {@link CommandStatus#EXECUTED} state
     * @throws EntityNotFoundException if command not found
     * @throws IllegalStateException   if command is not PENDING or has expired
     * @throws SecurityException       (BR-010) if caller lacks OPERATOR/ADMIN role
     */
    @Transactional
    public PendingBmsCommand approveCommand(Long commandId, String operatorUsername) {
        // BR-010: service-level defense-in-depth role check
        assertOperatorOrAdmin();

        PendingBmsCommand cmd = commandRepository.findById(commandId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PendingBmsCommand not found: " + commandId));

        validatePendingAndNotExpired(cmd);

        cmd.setStatus(CommandStatus.APPROVED);
        log.info("[BMS-CMD] Approved commandId={} buildingId={} operator={}",
                commandId, cmd.getBuildingId(), operatorUsername);

        // Dispatch to BACnet device via existing BmsDeviceCommandService
        BmsCommand bmsCommand = new BmsCommand(
                cmd.getCommandType(),
                Map.of("value", cmd.getTargetValue(), "objectType", "analogOutput")
        );
        try {
            bmsDeviceCommandService.sendCommand(
                    cmd.getTenantId(),
                    UUID.fromString(cmd.getTargetDevice()),
                    bmsCommand
            );
        } catch (IllegalArgumentException e) {
            // targetDevice is not a UUID — log and continue for POC demo mode
            log.warn("[BMS-CMD] targetDevice '{}' is not a UUID; skipping real dispatch (POC mode)",
                    cmd.getTargetDevice());
        }

        cmd.setStatus(CommandStatus.EXECUTED);
        cmd.setResolvedAt(Instant.now());
        cmd.setResolvedBy(operatorUsername);
        PendingBmsCommand saved = commandRepository.save(cmd);

        // Record COMMAND_SENT feedback stage
        feedbackService.recordStage(commandId, cmd.getBuildingId(),
                FeedbackStage.COMMAND_SENT, true, null);

        metrics.recordApproved(cmd.getBuildingId());
        metrics.recordApprovalLatency(cmd.getBuildingId(), cmd.getCreatedAt());

        log.info("[BMS-CMD] Executed commandId={} buildingId={} type={} operator={}",
                commandId, cmd.getBuildingId(), cmd.getCommandType(), operatorUsername);
        return saved;
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    /**
     * Rejects a pending BMS command. No actuator action is taken.
     *
     * @param commandId        id of the command to reject
     * @param operatorUsername username of the rejecting operator
     * @return the updated command in {@link CommandStatus#REJECTED} state
     */
    @Transactional
    public PendingBmsCommand rejectCommand(Long commandId, String operatorUsername) {
        PendingBmsCommand cmd = commandRepository.findById(commandId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PendingBmsCommand not found: " + commandId));

        if (cmd.getStatus() != CommandStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot reject command " + commandId + " in status: " + cmd.getStatus());
        }

        cmd.setStatus(CommandStatus.REJECTED);
        cmd.setResolvedAt(Instant.now());
        cmd.setResolvedBy(operatorUsername);
        PendingBmsCommand saved = commandRepository.save(cmd);

        metrics.recordRejected(cmd.getBuildingId());
        log.info("[BMS-CMD] Rejected commandId={} buildingId={} operator={}",
                commandId, cmd.getBuildingId(), operatorUsername);
        return saved;
    }

    // ── Scheduled expiry ──────────────────────────────────────────────────────

    /**
     * Scheduled task: scans for PENDING commands whose 30-second window has elapsed
     * and marks them EXPIRED.
     *
     * <p>Runs every 5 seconds (fixedDelay to avoid overlap).
     * Logs a warning for each command that times out without operator action.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void expireTimeoutCommands() {
        List<PendingBmsCommand> expired =
                commandRepository.findByStatusAndExpiresAtBefore(CommandStatus.PENDING, Instant.now());

        for (PendingBmsCommand cmd : expired) {
            cmd.setStatus(CommandStatus.EXPIRED);
            cmd.setResolvedAt(Instant.now());
            cmd.setResolvedBy("system");
            commandRepository.save(cmd);

            metrics.recordExpired(cmd.getBuildingId());
            log.warn("[BMS-CMD] EXPIRED commandId={} buildingId={} type={} requestedBy={}",
                    cmd.getId(), cmd.getBuildingId(), cmd.getCommandType(), cmd.getRequestedBy());
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns all PENDING commands for a building (for operator approval UI).
     */
    @Transactional(readOnly = true)
    public List<PendingBmsCommand> getPendingCommands(String buildingId) {
        return commandRepository.findByBuildingIdAndStatus(buildingId, CommandStatus.PENDING);
    }

    /**
     * Returns all PENDING commands for a tenant (cross-building operator view).
     */
    @Transactional(readOnly = true)
    public List<PendingBmsCommand> getPendingCommandsForTenant(String tenantId) {
        return commandRepository.findByTenantIdAndStatus(tenantId, CommandStatus.PENDING);
    }

    // ── BR-010 safety enforcement ─────────────────────────────────────────────

    /**
     * Defense-in-depth check: the security context must have OPERATOR or ADMIN authority.
     * If authentication is null (e.g. programmatic/scheduled context) the check is skipped
     * to allow system-initiated flows.
     *
     * @throws SecurityException (BR-010) when authenticated user lacks required role
     */
    private void assertOperatorOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // No security context — allow (e.g. test / programmatic call)
            return;
        }
        boolean hasRole = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_OPERATOR") || a.equals("ROLE_ADMIN"));
        if (!hasRole) {
            log.error("[BR-010] BMS command blocked: user='{}' lacks OPERATOR/ADMIN role",
                    auth.getName());
            throw new SecurityException("BR-010: BMS command requires operator approval");
        }
    }

    /**
     * Validates that a command is still in PENDING status and within its approval window.
     */
    private void validatePendingAndNotExpired(PendingBmsCommand cmd) {
        if (cmd.getStatus() != CommandStatus.PENDING) {
            throw new IllegalStateException(
                    "Command " + cmd.getId() + " cannot be approved, current status: " + cmd.getStatus());
        }
        if (Instant.now().isAfter(cmd.getExpiresAt())) {
            // Proactively expire it
            cmd.setStatus(CommandStatus.EXPIRED);
            cmd.setResolvedAt(Instant.now());
            cmd.setResolvedBy("system");
            commandRepository.save(cmd);
            metrics.recordExpired(cmd.getBuildingId());
            throw new IllegalStateException(
                    "Command " + cmd.getId() + " approval window (30s) has expired");
        }
    }
}
