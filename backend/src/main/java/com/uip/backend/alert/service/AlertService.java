package com.uip.backend.alert.service;

import com.uip.backend.alert.api.dto.AcknowledgeRequest;
import com.uip.backend.alert.api.dto.AlertEventDto;
import com.uip.backend.alert.api.dto.AlertRuleRequest;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.alert.repository.AlertRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    private final AlertEventRepository alertEventRepository;
    private final AlertRuleRepository  alertRuleRepository;

    // ─── Alert Events ─────────────────────────────────────────────────────────

    public Page<AlertEventDto> queryAlerts(String status, String severity,
                                           Instant from, Instant to,
                                           int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "detectedAt"));

        Specification<AlertEvent> spec = Specification.where(null);
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (severity != null && !severity.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("severity"), severity));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("detectedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("detectedAt"), to));
        }

        return alertEventRepository.findAll(spec, pageable)
                .map(this::toDto);
    }

    @Transactional
    public AlertEventDto acknowledgeAlert(UUID alertId, String username, AcknowledgeRequest req) {
        AlertEvent event = alertEventRepository.findById(alertId)
                .orElseThrow(() -> new EntityNotFoundException("Alert not found: " + alertId));

        event.setStatus("ACKNOWLEDGED");
        event.setAcknowledgedBy(username);
        event.setAcknowledgedAt(Instant.now());
        if (req.getNote() != null) {
            event.setNote(req.getNote());
        }
        return toDto(alertEventRepository.save(event));
    }

    // ─── Alert Rules (Admin) ──────────────────────────────────────────────────

    public List<AlertRule> listRules() {
        return alertRuleRepository.findByActiveTrueOrderByModuleAsc();
    }

    @Transactional
    public AlertRule createRule(AlertRuleRequest req) {
        AlertRule rule = new AlertRule();
        rule.setRuleName(req.getRuleName());
        rule.setModule(req.getModule());
        rule.setMeasureType(req.getMeasureType());
        rule.setOperator(req.getOperator());
        rule.setThreshold(req.getThreshold());
        rule.setSeverity(req.getSeverity());
        rule.setCooldownMinutes(req.getCooldownMinutes());
        return alertRuleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(UUID ruleId) {
        AlertRule rule = alertRuleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + ruleId));
        rule.setActive(false);
        alertRuleRepository.save(rule);
    }

    // ─── Mapper ───────────────────────────────────────────────────────────────

    private AlertEventDto toDto(AlertEvent a) {
        return AlertEventDto.builder()
                .id(a.getId())
                .ruleId(a.getRuleId())
                .sensorId(a.getSensorId())
                .module(a.getModule())
                .measureType(a.getMeasureType())
                .value(a.getValue())
                .threshold(a.getThreshold())
                .severity(a.getSeverity())
                .status(a.getStatus())
                .detectedAt(a.getDetectedAt())
                .acknowledgedBy(a.getAcknowledgedBy())
                .acknowledgedAt(a.getAcknowledgedAt())
                .note(a.getNote())
                .build();
    }
}
