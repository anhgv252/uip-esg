package com.uip.backend.alert.adapter;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.service.AlertService;
import com.uip.backend.common.spi.AlertPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code alert}-side implementation of {@link AlertPort} (ADR-052, migration C1).
 *
 * <p>Maps neutral {@link StructuralAlertInput} to the internal {@link AlertEvent} entity,
 * persists it, and projects the saved entity to a neutral {@link SavedAlertSnapshot} —
 * so the {@code safety} module never imports {@code alert.domain} / {@code alert.service}.</p>
 */
@Component
@RequiredArgsConstructor
public class AlertPortAdapter implements AlertPort {

    private final AlertService alertService;

    @Override
    public List<StructuralAlertSnapshot> findOpenStructuralAlerts(String buildingId, java.time.Instant since) {
        return alertService.findOpenStructuralAlerts(buildingId, since).stream()
                .map(event -> new StructuralAlertSnapshot(event.getSeverity()))
                .toList();
    }

    @Override
    public SavedAlertSnapshot saveStructuralAlert(StructuralAlertInput input) {
        AlertEvent event = new AlertEvent();
        event.setTenantId(input.tenantId());
        event.setSensorId(input.sensorId());
        event.setModule(input.module());
        event.setMeasureType(input.measureType());
        event.setValue(input.value());
        event.setThreshold(input.threshold());
        event.setSeverity(input.severity());
        event.setBuildingId(input.buildingId());
        event.setLocation(input.location());
        event.setDetectedAt(input.detectedAt());
        event.setStatus(input.status());

        AlertEvent saved = alertService.saveAlert(event);
        return new SavedAlertSnapshot(
                saved.getId(),
                saved.getSensorId(),
                saved.getSeverity(),
                saved.getBuildingId(),
                saved.getTenantId()
        );
    }
}
