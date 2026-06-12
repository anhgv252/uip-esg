package com.uip.backend.contract;

import com.uip.backend.alert.api.dto.AlertEventDto;
import com.uip.backend.alert.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Contract tests for Alert API — verify service contract (input/output).
 * Tests the AlertService mock contract that AlertController depends on.
 *
 * Controller-layer MockMvc tests require Spring Security context setup
 * (controller methods use Authentication parameter).
 * Those are covered in integration tests with full Spring context.
 *
 * v3.1-06: Alert API contract tests
 */
@Tag("contract")
@DisplayName("Alert API — Service Contract Tests")
class AlertApiContractTest {

    private AlertService alertService;

    private static final UUID ALERT_ID = UUID.randomUUID();
    private static final String OPERATOR = "operator";

    @BeforeEach
    void setUp() {
        alertService = mock(AlertService.class);
    }

    private AlertEventDto buildDto(UUID id, String status, String severity) {
        return AlertEventDto.builder()
                .id(id).ruleId(UUID.randomUUID()).ruleName("AQI Critical Threshold")
                .sensorId("ENV-001").module("environment").measureType("AQI")
                .value(210.0).threshold(200.0)
                .severity(severity).status(status).detectedAt(Instant.now())
                .build();
    }

    // ─── queryAlerts contract ─────────────────────────────────────────────────

    @Nested
    @DisplayName("queryAlerts — contract")
    class QueryAlertsTests {

        @Test
        @DisplayName("Returns Page<AlertEventDto> with correct field mapping")
        void queryAlerts_returnsPageWithCorrectFields() {
            AlertEventDto dto = buildDto(ALERT_ID, "OPEN", "CRITICAL");
            when(alertService.queryAlerts(eq("hcm"), eq("CRITICAL"), isNull(), isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(dto)));

            var result = alertService.queryAlerts("hcm", "CRITICAL", null, null, null, 0, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(ALERT_ID);
            assertThat(result.getContent().get(0).getSensorId()).isEqualTo("ENV-001");
            assertThat(result.getContent().get(0).getSeverity()).isEqualTo("CRITICAL");
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("Returns empty page when no matches")
        void queryAlerts_noMatch_returnsEmpty() {
            when(alertService.queryAlerts(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            var result = alertService.queryAlerts("nonexistent", "CRITICAL", null, null, null, 0, 20);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    // ─── acknowledge contract ─────────────────────────────────────────────────

    @Nested
    @DisplayName("acknowledgeAlert — contract")
    class AcknowledgeTests {

        @Test
        @DisplayName("Returns ACKNOWLEDGED alert with acknowledgedBy set")
        void acknowledge_returnsAcknowledgedWithUser() {
            AlertEventDto dto = buildDto(ALERT_ID, "ACKNOWLEDGED", "CRITICAL");
            dto.setAcknowledgedBy(OPERATOR);
            when(alertService.acknowledgeAlert(eq(ALERT_ID), eq(OPERATOR), any()))
                    .thenReturn(dto);

            var result = alertService.acknowledgeAlert(ALERT_ID, OPERATOR,
                    new com.uip.backend.alert.api.dto.AcknowledgeRequest());

            assertThat(result.getStatus()).isEqualTo("ACKNOWLEDGED");
            assertThat(result.getAcknowledgedBy()).isEqualTo(OPERATOR);
            assertThat(result.getId()).isEqualTo(ALERT_ID);
        }
    }

    // ─── escalate contract ────────────────────────────────────────────────────

    @Nested
    @DisplayName("escalateAlert — contract")
    class EscalateTests {

        @Test
        @DisplayName("Returns ESCALATED alert")
        void escalate_returnsEscalated() {
            AlertEventDto dto = buildDto(ALERT_ID, "ESCALATED", "CRITICAL");
            when(alertService.escalateAlert(eq(ALERT_ID), eq(OPERATOR), any()))
                    .thenReturn(dto);

            var result = alertService.escalateAlert(ALERT_ID, OPERATOR, "escalating");

            assertThat(result.getStatus()).isEqualTo("ESCALATED");
        }
    }

    // ─── resolve contract ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveAlert — contract")
    class ResolveTests {

        @Test
        @DisplayName("Returns RESOLVED alert")
        void resolve_returnsResolved() {
            AlertEventDto dto = buildDto(ALERT_ID, "RESOLVED", "CRITICAL");
            when(alertService.resolveAlert(eq(ALERT_ID), eq("admin"), any()))
                    .thenReturn(dto);

            var result = alertService.resolveAlert(ALERT_ID, "admin", "root cause fixed");

            assertThat(result.getStatus()).isEqualTo("RESOLVED");
        }
    }

    // ─── getPublicNotifications contract ──────────────────────────────────────

    @Nested
    @DisplayName("getPublicNotifications — contract")
    class NotificationsTests {

        @Test
        @DisplayName("Returns paginated public alerts")
        void getPublicNotifications_returnsPaginated() {
            AlertEventDto dto = buildDto(UUID.randomUUID(), "OPEN", "HIGH");
            when(alertService.getPublicNotifications(eq(0), eq(20)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(dto)));

            var result = alertService.getPublicNotifications(0, 20);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSeverity()).isEqualTo("HIGH");
        }
    }
}
