package com.uip.backend.bms;

import com.uip.backend.bms.domain.BmsFeedbackEvent;
import com.uip.backend.bms.domain.CommandStatus;
import com.uip.backend.bms.domain.FeedbackStage;
import com.uip.backend.bms.domain.PendingBmsCommand;
import com.uip.backend.bms.repository.BmsFeedbackEventRepository;
import com.uip.backend.bms.repository.PendingBmsCommandRepository;
import com.uip.backend.bms.service.BmsCommandService;
import com.uip.backend.bms.service.BmsDeviceCommandService;
import com.uip.backend.bms.service.BmsFeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.uip.backend.bms.domain.FeedbackStage.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BMS Closed-Loop Command Workflow — Contract Tests (M4-COR-03 / M4-COR-04).
 *
 * <p>Pure unit tests with Mockito — no Spring context, no database, no Docker.
 * Verifies the BR-010 safety contract: no BMS command executes without explicit
 * operator approval via {@link BmsCommandService}.
 *
 * <p>Services under test:
 * <ul>
 *   <li>{@link BmsCommandService} — propose / approve / reject / expire lifecycle</li>
 *   <li>{@link BmsFeedbackService} — 4-stage feedback loop (COMMAND_SENT → FEEDBACK_VERIFIED)</li>
 * </ul>
 *
 * <p>Tests BMS-01 through BMS-08 cover all BR-010 safety scenarios.
 */
@Tag("bms-workflow")
@ExtendWith(MockitoExtension.class)
@DisplayName("BMS Closed-Loop Workflow Contract Tests (M4-COR-03/04, BR-010)")
class BmsSimulatorIntegrationTest {

    // ─── BmsCommandService under test ────────────────────────────────────────────

    @Mock
    private PendingBmsCommandRepository commandRepository;

    @Mock
    private BmsDeviceCommandService bmsDeviceCommandService;

    @Mock
    private BmsFeedbackService feedbackService;

    @Mock
    private BmsCommandMetrics metrics;

    @InjectMocks
    private BmsCommandService bmsCommandService;

    // ─── Shared test fixtures ─────────────────────────────────────────────────────

    private static final String BUILDING_A = "BUILDING-HCM-01";
    private static final String TENANT     = "hcm";
    private static final String OPERATOR   = "operator.nguyen";

    @BeforeEach
    void resetMocks() {
        // MockitoExtension resets mocks between tests; no additional setup needed.
    }

    // ─── BMS-01: Normal flow — propose → approve → execute ──────────────────────

    @Test
    @DisplayName("BMS-01: Normal flow — propose → PENDING, approve → EXECUTED, feedback COMMAND_SENT recorded")
    void normalFlow_commandProposedApprovedExecuted() {
        // ── Arrange ──
        PendingBmsCommand pending = buildPendingCommand(1L, BUILDING_A, CommandStatus.PENDING);
        when(commandRepository.save(any(PendingBmsCommand.class))).thenReturn(pending);

        // ── Act: Step 1 — propose ──
        PendingBmsCommand proposed = bmsCommandService.proposeCommand(
                BUILDING_A, TENANT, "HVAC_OFF", pending.getTargetDevice(), "OFF", "incident-uuid-001");

        // ── Assert: PENDING created, no dispatch yet ──
        assertThat(proposed.getStatus()).isEqualTo(CommandStatus.PENDING);
        verify(commandRepository).save(any(PendingBmsCommand.class));
        verify(metrics).recordProposed(BUILDING_A);
        verify(bmsDeviceCommandService, never()).sendCommand(any(), any(), any());

        // ── Act: Step 2 — approve ──
        when(commandRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(commandRepository.save(pending)).thenReturn(pending);

        bmsCommandService.approveCommand(1L, OPERATOR);

        // ── Assert: dispatched to BMS device, feedback stage recorded, status = EXECUTED ──
        verify(bmsDeviceCommandService).sendCommand(
                eq(TENANT), any(UUID.class), any(com.uip.backend.bms.api.dto.BmsCommand.class));
        verify(feedbackService).recordStage(
                eq(1L), eq(BUILDING_A), eq(COMMAND_SENT), eq(true), isNull());
        assertThat(pending.getStatus()).isEqualTo(CommandStatus.EXECUTED);
        assertThat(pending.getResolvedBy()).isEqualTo(OPERATOR);
        verify(metrics).recordApproved(BUILDING_A);
    }

    // ─── BMS-02: Operator rejects — no BMS dispatch ───────────────────────────────

    @Test
    @DisplayName("BMS-02: Operator rejects → REJECTED, resolvedBy set, BMS device NOT activated")
    void operatorRejects_commandCancelled() {
        // ── Arrange ──
        PendingBmsCommand pending = buildPendingCommand(2L, BUILDING_A, CommandStatus.PENDING);
        when(commandRepository.save(any())).thenReturn(pending);

        // Propose
        bmsCommandService.proposeCommand(BUILDING_A, TENANT, "SPRINKLER_ON",
                pending.getTargetDevice(), "ON", "safety-system");

        // ── Act: operator rejects ──
        when(commandRepository.findById(2L)).thenReturn(Optional.of(pending));
        when(commandRepository.save(pending)).thenReturn(pending);

        PendingBmsCommand rejected = bmsCommandService.rejectCommand(2L, OPERATOR);

        // ── Assert ──
        assertThat(rejected.getStatus()).isEqualTo(CommandStatus.REJECTED);
        assertThat(rejected.getResolvedBy()).isEqualTo(OPERATOR);
        assertThat(rejected.getResolvedAt()).isNotNull();
        verify(bmsDeviceCommandService, never()).sendCommand(any(), any(), any());
        verify(feedbackService, never()).recordStage(any(), any(), any(), anyBoolean(), any());
        verify(metrics).recordRejected(BUILDING_A);
    }

    // ─── BMS-03: Command timeout — auto-expired by scheduler ─────────────────────

    @Test
    @DisplayName("BMS-03: 30s window elapsed — expireTimeoutCommands() marks EXPIRED, metrics recorded")
    void commandTimeout_autoExpired() {
        // ── Arrange: a command whose approval window has passed ──
        PendingBmsCommand stale = buildPendingCommand(3L, BUILDING_A, CommandStatus.PENDING);
        stale.setExpiresAt(Instant.now().minusSeconds(5));  // Already 5s past deadline

        when(commandRepository.findByStatusAndExpiresAtBefore(
                eq(CommandStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(stale));

        // ── Act: scheduler task runs ──
        bmsCommandService.expireTimeoutCommands();

        // ── Assert: command is EXPIRED, saved, metrics fired ──
        assertThat(stale.getStatus()).isEqualTo(CommandStatus.EXPIRED);
        assertThat(stale.getResolvedBy()).isEqualTo("system");
        assertThat(stale.getResolvedAt()).isNotNull();
        verify(commandRepository).save(stale);
        verify(metrics).recordExpired(BUILDING_A);
        // BMS device must NOT have been activated
        verify(bmsDeviceCommandService, never()).sendCommand(any(), any(), any());
    }

    // ─── BMS-04: Approve expired command → throws ─────────────────────────────────

    @Test
    @DisplayName("BMS-04: approveCommand() on EXPIRED command → IllegalStateException, no BMS dispatch")
    void approveExpiredCommand_throwsException() {
        // ── Arrange: command already in EXPIRED state (timeout ran before operator acted) ──
        PendingBmsCommand expired = buildPendingCommand(4L, BUILDING_A, CommandStatus.EXPIRED);

        when(commandRepository.findById(4L)).thenReturn(Optional.of(expired));

        // ── Act + Assert ──
        assertThatThrownBy(() -> bmsCommandService.approveCommand(4L, OPERATOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXPIRED");

        // BR-010: no BMS action taken when approval window is closed
        verify(bmsDeviceCommandService, never()).sendCommand(any(), any(), any());
        verify(feedbackService, never()).recordStage(any(), any(), any(), anyBoolean(), any());
    }

    // ─── BMS-05: Feedback loop — all 4 stages complete → isLoopComplete() = true ─

    @Test
    @DisplayName("BMS-05: Feedback loop — all 4 stages recorded → isLoopComplete() = true")
    void feedbackLoop_allStagesComplete() {
        // ── Arrange: fresh BmsFeedbackService with isolated mock repository ──
        BmsFeedbackEventRepository feedbackRepo = mock(BmsFeedbackEventRepository.class);
        BmsFeedbackService service = new BmsFeedbackService(feedbackRepo);
        Long commandId = 5L;

        BmsFeedbackEvent successEvent = buildFeedbackEvent(commandId, true);
        // For every stage query, return one successful event
        when(feedbackRepo.findByPendingCommandIdAndStage(eq(commandId), any(FeedbackStage.class)))
                .thenReturn(List.of(successEvent));

        // ── Act: record all 4 loop stages ──
        service.recordStage(commandId, COMMAND_SENT, true, null);
        service.recordStage(commandId, COMMAND_ACKNOWLEDGED, true, null);
        service.recordStage(commandId, ACTION_TAKEN, true, null);
        service.recordStage(commandId, FEEDBACK_VERIFIED, true, null);

        // ── Assert ──
        verify(feedbackRepo, times(4)).save(any(BmsFeedbackEvent.class));
        assertThat(service.isLoopComplete(commandId))
                .as("All 4 stages succeeded — loop must be complete")
                .isTrue();
    }

    // ─── BMS-06: Feedback loop — missing stage → isLoopComplete() = false ─────────

    @Test
    @DisplayName("BMS-06: Feedback loop — FEEDBACK_VERIFIED missing → isLoopComplete() = false")
    void feedbackLoop_missingStage_incomplete() {
        // ── Arrange ──
        BmsFeedbackEventRepository feedbackRepo = mock(BmsFeedbackEventRepository.class);
        BmsFeedbackService service = new BmsFeedbackService(feedbackRepo);
        Long commandId = 6L;

        BmsFeedbackEvent successEvent = buildFeedbackEvent(commandId, true);
        // Default: all stages return a successful event
        when(feedbackRepo.findByPendingCommandIdAndStage(eq(commandId), any(FeedbackStage.class)))
                .thenReturn(List.of(successEvent));
        // Override: FEEDBACK_VERIFIED has not been recorded yet
        when(feedbackRepo.findByPendingCommandIdAndStage(eq(commandId), eq(FEEDBACK_VERIFIED)))
                .thenReturn(List.of());

        // ── Act: record only 3 of 4 stages ──
        service.recordStage(commandId, COMMAND_SENT, true, null);
        service.recordStage(commandId, COMMAND_ACKNOWLEDGED, true, null);
        service.recordStage(commandId, ACTION_TAKEN, true, null);

        // ── Assert ──
        verify(feedbackRepo, times(3)).save(any(BmsFeedbackEvent.class));
        assertThat(service.isLoopComplete(commandId))
                .as("FEEDBACK_VERIFIED is missing — loop must NOT be complete")
                .isFalse();
    }

    // ─── BMS-07: Safety constraint — proposeCommand never auto-executes ──────────

    @Test
    @DisplayName("BMS-07: BR-010 safety — proposeCommand() creates PENDING only, zero BMS dispatch")
    void safetyConstraint_noBmsWithoutApproval() {
        // ── Arrange ──
        PendingBmsCommand pending = buildPendingCommand(7L, BUILDING_A, CommandStatus.PENDING);
        when(commandRepository.save(any(PendingBmsCommand.class))).thenReturn(pending);

        // ── Act: propose command (no approval step) ──
        PendingBmsCommand result = bmsCommandService.proposeCommand(
                BUILDING_A, TENANT, "EVACUATION", pending.getTargetDevice(), "ACTIVATE", "safety-correlation");

        // ── Assert: status is PENDING, NOT EXECUTED ──
        assertThat(result.getStatus())
                .as("Proposed command must be PENDING — no auto-execution (BR-010)")
                .isEqualTo(CommandStatus.PENDING);
        assertThat(result.getStatus())
                .isNotEqualTo(CommandStatus.EXECUTED);

        // Absolute safety check: BMS device must never be called without approval
        verify(bmsDeviceCommandService, never()).sendCommand(any(), any(), any());
        verify(feedbackService, never()).recordStage(any(), any(), any(), anyBoolean(), any());
    }

    // ─── BMS-08: Multiple commands same building — independent tracking ───────────

    @Test
    @DisplayName("BMS-08: Two commands same building — approve one, reject other independently")
    void multipleCommandsSameBuilding_independentTracking() {
        // ── Arrange: two proposed commands for the same building ──
        PendingBmsCommand cmd1 = buildPendingCommand(8L, BUILDING_A, CommandStatus.PENDING);
        PendingBmsCommand cmd2 = buildPendingCommand(9L, BUILDING_A, CommandStatus.PENDING);
        cmd2.setCommandType("LIGHTS_OFF");

        // First save → cmd1, second save → cmd2
        when(commandRepository.save(any())).thenReturn(cmd1, cmd2);

        bmsCommandService.proposeCommand(BUILDING_A, TENANT, "HVAC_OFF",
                cmd1.getTargetDevice(), "OFF", "incident-001");
        bmsCommandService.proposeCommand(BUILDING_A, TENANT, "LIGHTS_OFF",
                cmd2.getTargetDevice(), "OFF", "incident-001");

        // ── Act: approve cmd1 ──
        when(commandRepository.findById(8L)).thenReturn(Optional.of(cmd1));
        when(commandRepository.save(cmd1)).thenReturn(cmd1);
        bmsCommandService.approveCommand(8L, "operator-alpha");

        // ── Act: reject cmd2 ──
        when(commandRepository.findById(9L)).thenReturn(Optional.of(cmd2));
        when(commandRepository.save(cmd2)).thenReturn(cmd2);
        bmsCommandService.rejectCommand(9L, "operator-beta");

        // ── Assert: each command resolved independently ──
        assertThat(cmd1.getStatus())
                .as("cmd1 (HVAC_OFF) should be EXECUTED after approval")
                .isEqualTo(CommandStatus.EXECUTED);
        assertThat(cmd1.getResolvedBy()).isEqualTo("operator-alpha");

        assertThat(cmd2.getStatus())
                .as("cmd2 (LIGHTS_OFF) should be REJECTED — independent decision")
                .isEqualTo(CommandStatus.REJECTED);
        assertThat(cmd2.getResolvedBy()).isEqualTo("operator-beta");

        // cmd1 triggered BMS dispatch, cmd2 did NOT
        verify(bmsDeviceCommandService, times(1)).sendCommand(any(), any(), any());
        verify(metrics, times(2)).recordProposed(BUILDING_A);
        verify(metrics, times(1)).recordApproved(BUILDING_A);
        verify(metrics, times(1)).recordRejected(BUILDING_A);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Builds a {@link PendingBmsCommand} POJO with sensible defaults for testing.
     *
     * @param id         synthetic command ID (simulates DB auto-generated value)
     * @param buildingId target building
     * @param status     initial {@link CommandStatus}
     */
    private PendingBmsCommand buildPendingCommand(Long id, String buildingId, CommandStatus status) {
        PendingBmsCommand cmd = new PendingBmsCommand();
        cmd.setId(id);
        cmd.setBuildingId(buildingId);
        cmd.setTenantId(TENANT);
        cmd.setCommandType("HVAC_OFF");
        // Use a valid UUID string so UUID.fromString() succeeds in approveCommand()
        cmd.setTargetDevice(UUID.randomUUID().toString());
        cmd.setTargetValue("OFF");
        cmd.setRequestedBy("system");
        cmd.setStatus(status);
        cmd.setCreatedAt(Instant.now());
        cmd.setExpiresAt(Instant.now().plusSeconds(30));
        return cmd;
    }

    /**
     * Builds a {@link BmsFeedbackEvent} for feedback loop assertions.
     *
     * @param commandId FK to PendingBmsCommand
     * @param success   whether this stage completed successfully
     */
    private BmsFeedbackEvent buildFeedbackEvent(Long commandId, boolean success) {
        BmsFeedbackEvent event = new BmsFeedbackEvent();
        event.setPendingCommandId(commandId);
        event.setSuccess(success);
        return event;
    }
}
