package com.uip.backend.alert.service;

import com.uip.backend.alert.api.dto.AcknowledgeRequest;
import com.uip.backend.alert.api.dto.AlertRuleRequest;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.alert.repository.AlertRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService")
class AlertServiceTest {

    @Mock private AlertEventRepository alertEventRepository;
    @Mock private AlertRuleRepository  alertRuleRepository;

    @InjectMocks private AlertService alertService;

    private AlertEvent openAlert;
    private UUID       alertId;

    @BeforeEach
    void setUp() {
        alertId = UUID.randomUUID();

        openAlert = new AlertEvent();
        openAlert.setId(alertId);
        openAlert.setSensorId("ENV-001");
        openAlert.setModule("environment");
        openAlert.setMeasureType("AQI");
        openAlert.setValue(210.0);
        openAlert.setThreshold(200.0);
        openAlert.setSeverity("CRITICAL");
        openAlert.setStatus("OPEN");
        openAlert.setDetectedAt(Instant.now().minusSeconds(120));
    }

    // ─── queryAlerts ────────────────────────────────────────────────────────

    @Test
    @DisplayName("queryAlerts: returns page of alerts matching filters")
    void queryAlerts_withFilters_returnsMappedPage() {
        var page = new PageImpl<>(List.of(openAlert));
        when(alertEventRepository.findAll(ArgumentMatchers.<Specification<AlertEvent>>any(), any(Pageable.class)))
                .thenReturn(page);

        var result = alertService.queryAlerts("OPEN", "CRITICAL",
                Instant.now().minusSeconds(3600), Instant.now(), 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSeverity()).isEqualTo("CRITICAL");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("queryAlerts: no filters returns all alerts")
    void queryAlerts_noFilters_returnsAll() {
        when(alertEventRepository.findAll(ArgumentMatchers.<Specification<AlertEvent>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(openAlert)));

        var result = alertService.queryAlerts(null, null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("queryAlerts: page size capped at 100")
    void queryAlerts_sizeOverMax_isCapped() {
        when(alertEventRepository.findAll(ArgumentMatchers.<Specification<AlertEvent>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        alertService.queryAlerts(null, null, null, null, 0, 999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(alertEventRepository).findAll(ArgumentMatchers.<Specification<AlertEvent>>any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    // ─── acknowledgeAlert ───────────────────────────────────────────────────

    @Test
    @DisplayName("acknowledgeAlert: sets ACKNOWLEDGED status and records username directly")
    void acknowledgeAlert_found_setsAcknowledged() {
        when(alertEventRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        AcknowledgeRequest req = new AcknowledgeRequest();
        req.setNote("Investigated and resolved");

        var result = alertService.acknowledgeAlert(alertId, "operator", req);

        assertThat(result.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(result.getAcknowledgedBy()).isEqualTo("operator");  // username string, no User lookup
        assertThat(result.getNote()).isEqualTo("Investigated and resolved");
        verify(alertEventRepository).save(argThat(e ->
                "ACKNOWLEDGED".equals(e.getStatus()) && e.getAcknowledgedAt() != null));
    }

    @Test
    @DisplayName("acknowledgeAlert: alert not found throws EntityNotFoundException")
    void acknowledgeAlert_alertNotFound_throwsException() {
        when(alertEventRepository.findById(alertId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                alertService.acknowledgeAlert(alertId, "operator", new AcknowledgeRequest()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(alertId.toString());
    }

    @Test
    @DisplayName("acknowledgeAlert: null note does not overwrite existing note")
    void acknowledgeAlert_nullNote_doesNotOverwrite() {
        openAlert.setNote("existing note");
        when(alertEventRepository.findById(alertId)).thenReturn(Optional.of(openAlert));
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AcknowledgeRequest req = new AcknowledgeRequest(); // note is null

        var result = alertService.acknowledgeAlert(alertId, "operator", req);

        assertThat(result.getNote()).isEqualTo("existing note");
    }

    // ─── listRules ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listRules: returns active rules in module order")
    void listRules_returnsActiveRules() {
        AlertRule rule = new AlertRule();
        rule.setRuleName("AQI CRITICAL");
        rule.setModule("environment");
        when(alertRuleRepository.findByActiveTrueOrderByModuleAsc()).thenReturn(List.of(rule));

        var result = alertService.listRules();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRuleName()).isEqualTo("AQI CRITICAL");
    }

    // ─── createRule ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("createRule: persists and returns new rule")
    void createRule_validRequest_savesRule() {
        AlertRuleRequest req = new AlertRuleRequest();
        req.setRuleName("PM2.5 WARNING");
        req.setModule("environment");
        req.setMeasureType("PM25");
        req.setOperator(">");
        req.setThreshold(35.5);
        req.setSeverity("WARNING");
        req.setCooldownMinutes(15);

        AlertRule saved = new AlertRule();
        saved.setRuleName("PM2.5 WARNING");
        when(alertRuleRepository.save(any(AlertRule.class))).thenReturn(saved);

        var result = alertService.createRule(req);

        assertThat(result.getRuleName()).isEqualTo("PM2.5 WARNING");
        verify(alertRuleRepository).save(argThat(r ->
                "PM2.5 WARNING".equals(r.getRuleName()) &&
                "environment".equals(r.getModule()) &&
                r.getThreshold() == 35.5));
    }

    // ─── deleteRule ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteRule: soft-deletes rule by setting active=false")
    void deleteRule_found_softDeletes() {
        UUID ruleId = UUID.randomUUID();
        AlertRule rule = new AlertRule();
        rule.setId(ruleId);
        rule.setActive(true);
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(alertRuleRepository.save(any())).thenReturn(rule);

        alertService.deleteRule(ruleId);

        verify(alertRuleRepository).save(argThat(r -> !r.isActive()));
    }

    @Test
    @DisplayName("deleteRule: rule not found throws EntityNotFoundException")
    void deleteRule_notFound_throwsException() {
        UUID ruleId = UUID.randomUUID();
        when(alertRuleRepository.findById(ruleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.deleteRule(ruleId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(ruleId.toString());
    }
}
