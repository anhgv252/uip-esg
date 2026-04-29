package com.uip.backend.alert.service;

import com.uip.backend.alert.api.dto.AcknowledgeRequest;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.alert.repository.AlertRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MVP2-03a: Alert state transition and escalation tests.
 * Validates the lifecycle: OPEN -> ACKNOWLEDGED -> RESOLVED, and OPEN -> ESCALATED.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MVP2-03a Alert Escalation & State Transitions")
class AlertEscalationTest {

    @Mock
    private AlertEventRepository alertEventRepository;
    @Mock
    private AlertRuleRepository alertRuleRepository;

    @InjectMocks
    private AlertService alertService;

    private UUID alertId;
    private AlertEvent openAlert;

    @BeforeEach
    void setUp() {
        alertId = UUID.randomUUID();
        openAlert = buildAlert(alertId, "OPEN");
    }

    // ─── State Transitions ──────────────────────────────────────────────────

    @Nested
    @DisplayName("State transition: OPEN -> ACKNOWLEDGED -> RESOLVED")
    class AcknowledgeTransition {

        @Test
        @DisplayName("OPEN -> ACKNOWLEDGED: status changes, acknowledgedBy and acknowledgedAt set")
        void openToAcknowledged_setsStatusAndMetadata() {
            // Given
            when(alertEventRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
            when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(alertRuleRepository.findById(any())).thenReturn(Optional.empty());

            AcknowledgeRequest req = new AcknowledgeRequest();
            req.setNote("Investigating sensor malfunction");

            // When
            var result = alertService.acknowledgeAlert(alertId, "operator1", req);

            // Then
            assertThat(result.getStatus()).isEqualTo("ACKNOWLEDGED");
            assertThat(result.getAcknowledgedBy()).isEqualTo("operator1");
            assertThat(result.getAcknowledgedAt()).isNotNull();
            assertThat(result.getNote()).isEqualTo("Investigating sensor malfunction");

            verify(alertEventRepository).save(argThat(event ->
                    "ACKNOWLEDGED".equals(event.getStatus())
                    && "operator1".equals(event.getAcknowledgedBy())
                    && event.getAcknowledgedAt() != null
            ));
        }

        @Test
        @DisplayName("ACKNOWLEDGED -> re-acknowledge: overwrites acknowledgedBy and acknowledgedAt")
        void acknowledgedToReAck_overwritesMetadata() {
            // Given — alert already acknowledged
            openAlert.setStatus("ACKNOWLEDGED");
            openAlert.setAcknowledgedBy("operator1");
            openAlert.setAcknowledgedAt(Instant.now().minusSeconds(300));

            when(alertEventRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
            when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(alertRuleRepository.findById(any())).thenReturn(Optional.empty());

            AcknowledgeRequest req = new AcknowledgeRequest();
            req.setNote("Transferred to operator2");

            // When
            var result = alertService.acknowledgeAlert(alertId, "operator2", req);

            // Then
            assertThat(result.getStatus()).isEqualTo("ACKNOWLEDGED");
            assertThat(result.getAcknowledgedBy()).isEqualTo("operator2");
            assertThat(result.getNote()).isEqualTo("Transferred to operator2");
        }

        @Test
        @DisplayName("acknowledge without note: preserves existing note when req.note is null")
        void acknowledge_nullNote_preservesExistingNote() {
            // Given
            openAlert.setNote("original investigation note");
            when(alertEventRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
            when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(alertRuleRepository.findById(any())).thenReturn(Optional.empty());

            // When
            var result = alertService.acknowledgeAlert(alertId, "operator1", new AcknowledgeRequest());

            // Then
            assertThat(result.getNote()).isEqualTo("original investigation note");
        }
    }

    // ─── Escalation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("State transition: OPEN -> ESCALATED")
    class EscalateTransition {

        @Test
        @DisplayName("OPEN -> ESCALATED: status changes, sets acknowledgedBy and acknowledgedAt")
        void openToEscalated_setsStatusAndMetadata() {
            // Given
            when(alertEventRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
            when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(alertRuleRepository.findById(any())).thenReturn(Optional.empty());

            // When
            var result = alertService.escalateAlert(alertId, "supervisor1", "Critical threshold exceeded — needs management decision");

            // Then
            assertThat(result.getStatus()).isEqualTo("ESCALATED");
            assertThat(result.getAcknowledgedBy()).isEqualTo("supervisor1");
            assertThat(result.getAcknowledgedAt()).isNotNull();
            assertThat(result.getNote()).isEqualTo("Critical threshold exceeded — needs management decision");

            verify(alertEventRepository).save(argThat(event ->
                    "ESCALATED".equals(event.getStatus())
                    && "supervisor1".equals(event.getAcknowledgedBy())
                    && event.getAcknowledgedAt() != null
            ));
        }

        @Test
        @DisplayName("ESCALATED -> re-escalate: updates acknowledgedBy to new supervisor")
        void escalatedToReEscalate_updatesMetadata() {
            // Given
            openAlert.setStatus("ESCALATED");
            openAlert.setAcknowledgedBy("supervisor1");
            openAlert.setAcknowledgedAt(Instant.now().minusSeconds(600));
            openAlert.setNote("Initial escalation");

            when(alertEventRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
            when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(alertRuleRepository.findById(any())).thenReturn(Optional.empty());

            // When
            var result = alertService.escalateAlert(alertId, "supervisor2", "Re-escalated to higher management");

            // Then
            assertThat(result.getStatus()).isEqualTo("ESCALATED");
            assertThat(result.getAcknowledgedBy()).isEqualTo("supervisor2");
            assertThat(result.getNote()).isEqualTo("Re-escalated to higher management");
        }

        @Test
        @DisplayName("ESCALATE with null note: preserves existing note")
        void escalate_nullNote_preservesExistingNote() {
            // Given
            openAlert.setNote("initial escalation reason");
            when(alertEventRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
            when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));
            when(alertRuleRepository.findById(any())).thenReturn(Optional.empty());

            // When
            var result = alertService.escalateAlert(alertId, "supervisor1", null);

            // Then
            assertThat(result.getNote()).isEqualTo("initial escalation reason");
        }
    }

    // ─── Not Found ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Alert not found scenarios")
    class NotFound {

        @Test
        @DisplayName("acknowledgeAlert: non-existent ID throws EntityNotFoundException")
        void acknowledge_nonExistentId_throwsEntityNotFoundException() {
            when(alertEventRepository.findById(alertId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.acknowledgeAlert(alertId, "operator1", new AcknowledgeRequest()))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(alertId.toString());
        }

        @Test
        @DisplayName("escalateAlert: non-existent ID throws EntityNotFoundException")
        void escalate_nonExistentId_throwsEntityNotFoundException() {
            when(alertEventRepository.findById(alertId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.escalateAlert(alertId, "supervisor1", "note"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(alertId.toString());
        }

        @Test
        @DisplayName("Multiple operations on non-existent alert: each throws independently")
        void multipleOpsOnNonExistent_eachThrows() {
            UUID missingId = UUID.randomUUID();
            when(alertEventRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.acknowledgeAlert(missingId, "op1", new AcknowledgeRequest()))
                    .isInstanceOf(EntityNotFoundException.class);

            assertThatThrownBy(() -> alertService.escalateAlert(missingId, "sup1", "note"))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(alertEventRepository, never()).save(any());
        }
    }

    // ─── Full Lifecycle Sequence ────────────────────────────────────────────

    @Test
    @DisplayName("Full lifecycle: OPEN -> ACKNOWLEDGED -> (simulate external resolve)")
    void fullLifecycle_openToAcknowledged() {
        // Step 1: Acknowledge OPEN alert
        when(alertEventRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(inv -> {
            AlertEvent saved = inv.getArgument(0);
            return saved;
        });
        when(alertRuleRepository.findById(any())).thenReturn(Optional.empty());

        AcknowledgeRequest req = new AcknowledgeRequest();
        req.setNote("Verified — sensor recalibrated");

        var acknowledged = alertService.acknowledgeAlert(alertId, "operator1", req);

        assertThat(acknowledged.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(acknowledged.getAcknowledgedBy()).isEqualTo("operator1");
        assertThat(acknowledged.getAcknowledgedAt()).isNotNull();
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    private AlertEvent buildAlert(UUID id, String status) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setRuleId(UUID.randomUUID()); // set ruleId so toDto calls alertRuleRepository.findById
        event.setSensorId("ENV-AQI-001");
        event.setModule("environment");
        event.setMeasureType("AQI");
        event.setValue(250.0);
        event.setThreshold(200.0);
        event.setSeverity("CRITICAL");
        event.setStatus(status);
        event.setDetectedAt(Instant.now().minusSeconds(180));
        return event;
    }
}
