package com.uip.backend.bms.service;

import com.uip.backend.bms.BmsCommandMetrics;
import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.domain.CommandStatus;
import com.uip.backend.bms.domain.FeedbackStage;
import com.uip.backend.bms.domain.PendingBmsCommand;
import com.uip.backend.bms.repository.PendingBmsCommandRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BmsCommandService — unit")
class BmsCommandServiceTest {

    @Mock private PendingBmsCommandRepository commandRepository;
    @Mock private BmsDeviceCommandService bmsDeviceCommandService;
    @Mock private BmsFeedbackService feedbackService;
    @Mock private BmsCommandMetrics metrics;

    @InjectMocks private BmsCommandService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── proposeCommand ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("proposeCommand")
    class ProposeCommand {

        @Test
        @DisplayName("creates PENDING command with expiresAt = now + 30s")
        void createsPendingWithCorrectExpiry() {
            when(commandRepository.save(any())).thenAnswer(inv -> {
                PendingBmsCommand cmd = inv.getArgument(0);
                cmd.setId(1L);
                return cmd;
            });

            Instant before = Instant.now();
            PendingBmsCommand result = service.proposeCommand(
                    "B001", "hcm", "HVAC_OFF",
                    UUID.randomUUID().toString(), "0", "incident-99");
            Instant after = Instant.now();

            assertThat(result.getStatus()).isEqualTo(CommandStatus.PENDING);
            assertThat(result.getBuildingId()).isEqualTo("B001");
            assertThat(result.getCommandType()).isEqualTo("HVAC_OFF");
            assertThat(result.getExpiresAt()).isBetween(
                    before.plusSeconds(29), after.plusSeconds(31));
            assertThat(result.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("increments proposed metric")
        void incrementsProposedMetric() {
            when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.proposeCommand("B001", "hcm", "HVAC_OFF",
                    UUID.randomUUID().toString(), "0", "test");

            verify(metrics).recordProposed("B001");
        }
    }

    // ── approveCommand ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveCommand")
    class ApproveCommand {

        private PendingBmsCommand pendingCmd;
        private UUID deviceId;

        @BeforeEach
        void setUp() {
            deviceId = UUID.randomUUID();
            pendingCmd = new PendingBmsCommand();
            pendingCmd.setId(10L);
            pendingCmd.setBuildingId("B001");
            pendingCmd.setTenantId("hcm");
            pendingCmd.setCommandType("SPRINKLER_ON");
            pendingCmd.setTargetDevice(deviceId.toString());
            pendingCmd.setTargetValue("1");
            pendingCmd.setStatus(CommandStatus.PENDING);
            pendingCmd.setCreatedAt(Instant.now());
            pendingCmd.setExpiresAt(Instant.now().plusSeconds(30));

            when(commandRepository.findById(10L)).thenReturn(Optional.of(pendingCmd));
            when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(bmsDeviceCommandService.sendCommand(anyString(), eq(deviceId), any(BmsCommand.class)))
                    .thenReturn("cmd-dispatch-123");
        }

        @Test
        @DisplayName("sets status to EXECUTED and calls sendCommand")
        void setsExecutedAndCallsSendCommand() {
            PendingBmsCommand result = service.approveCommand(10L, "operator1");

            assertThat(result.getStatus()).isEqualTo(CommandStatus.EXECUTED);
            assertThat(result.getResolvedBy()).isEqualTo("operator1");
            assertThat(result.getResolvedAt()).isNotNull();

            verify(bmsDeviceCommandService).sendCommand(
                    eq("hcm"), eq(deviceId), argThat(cmd -> "SPRINKLER_ON".equals(cmd.commandType())));
        }

        @Test
        @DisplayName("records COMMAND_SENT feedback stage after dispatch")
        void recordsFeedbackStageCommandSent() {
            service.approveCommand(10L, "operator1");

            verify(feedbackService).recordStage(eq(10L), eq("B001"),
                    eq(FeedbackStage.COMMAND_SENT), eq(true), isNull());
        }

        @Test
        @DisplayName("increments approved metric and records latency")
        void incrementsApprovedMetricAndLatency() {
            service.approveCommand(10L, "operator1");

            verify(metrics).recordApproved("B001");
            verify(metrics).recordApprovalLatency(eq("B001"), any(Instant.class));
        }

        @Test
        @DisplayName("throws IllegalStateException when status is EXPIRED")
        void throwsWhenExpired() {
            pendingCmd.setStatus(CommandStatus.EXPIRED);

            assertThatThrownBy(() -> service.approveCommand(10L, "operator1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EXPIRED");
        }

        @Test
        @DisplayName("throws IllegalStateException when status is REJECTED")
        void throwsWhenRejected() {
            pendingCmd.setStatus(CommandStatus.REJECTED);

            assertThatThrownBy(() -> service.approveCommand(10L, "operator1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REJECTED");
        }

        @Test
        @DisplayName("throws EntityNotFoundException when command not found")
        void throwsWhenNotFound() {
            when(commandRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approveCommand(99L, "operator1"))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("BR-010: throws SecurityException when authenticated user lacks OPERATOR/ADMIN role")
        void throwsSecurityExceptionForNonOperatorRole() {
            // Set security context with USER role (no OPERATOR/ADMIN)
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "citizen1", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertThatThrownBy(() -> service.approveCommand(10L, "citizen1"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("BR-010");
        }

        @Test
        @DisplayName("allows approve when security context has OPERATOR role")
        void allowsApproveWithOperatorRole() {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "operator1", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            PendingBmsCommand result = service.approveCommand(10L, "operator1");

            assertThat(result.getStatus()).isEqualTo(CommandStatus.EXECUTED);
        }
    }

    // ── rejectCommand ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectCommand")
    class RejectCommand {

        @Test
        @DisplayName("sets status to REJECTED with operator and timestamp")
        void setsRejected() {
            PendingBmsCommand cmd = pendingCommand(CommandStatus.PENDING);
            when(commandRepository.findById(20L)).thenReturn(Optional.of(cmd));
            when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PendingBmsCommand result = service.rejectCommand(20L, "operator2");

            assertThat(result.getStatus()).isEqualTo(CommandStatus.REJECTED);
            assertThat(result.getResolvedBy()).isEqualTo("operator2");
            assertThat(result.getResolvedAt()).isNotNull();
            verify(metrics).recordRejected("B001");
        }

        @Test
        @DisplayName("throws when command is not PENDING")
        void throwsWhenNotPending() {
            PendingBmsCommand cmd = pendingCommand(CommandStatus.EXECUTED);
            when(commandRepository.findById(21L)).thenReturn(Optional.of(cmd));

            assertThatThrownBy(() -> service.rejectCommand(21L, "operator2"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ── expireTimeoutCommands ─────────────────────────────────────────────────

    @Nested
    @DisplayName("expireTimeoutCommands (scheduled)")
    class ExpireTimeoutCommands {

        @Test
        @DisplayName("marks PENDING commands with past expiresAt as EXPIRED")
        void expiresPastDueCommands() {
            PendingBmsCommand stale = pendingCommand(CommandStatus.PENDING);
            stale.setExpiresAt(Instant.now().minusSeconds(60)); // already past

            when(commandRepository.findByStatusAndExpiresAtBefore(
                    eq(CommandStatus.PENDING), any(Instant.class)))
                    .thenReturn(List.of(stale));
            when(commandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.expireTimeoutCommands();

            ArgumentCaptor<PendingBmsCommand> captor = ArgumentCaptor.forClass(PendingBmsCommand.class);
            verify(commandRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(CommandStatus.EXPIRED);
            assertThat(captor.getValue().getResolvedBy()).isEqualTo("system");

            verify(metrics).recordExpired("B001");
        }

        @Test
        @DisplayName("no-op when no commands are expired")
        void noopWhenNoneExpired() {
            when(commandRepository.findByStatusAndExpiresAtBefore(any(), any()))
                    .thenReturn(List.of());

            service.expireTimeoutCommands();

            verify(commandRepository, never()).save(any());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PendingBmsCommand pendingCommand(CommandStatus status) {
        PendingBmsCommand cmd = new PendingBmsCommand();
        cmd.setId(20L);
        cmd.setBuildingId("B001");
        cmd.setTenantId("hcm");
        cmd.setCommandType("HVAC_OFF");
        cmd.setTargetDevice(UUID.randomUUID().toString());
        cmd.setTargetValue("0");
        cmd.setStatus(status);
        cmd.setCreatedAt(Instant.now().minusSeconds(5));
        cmd.setExpiresAt(Instant.now().plusSeconds(25));
        return cmd;
    }
}
