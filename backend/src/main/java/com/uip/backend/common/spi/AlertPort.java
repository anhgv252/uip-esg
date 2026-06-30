package com.uip.backend.common.spi;

import java.time.Instant;
import java.util.List;

/**
 * Port for the {@code safety} module to interact with the {@code alert} module without
 * depending on {@code alert.domain} / {@code alert.service} (ADR-052, migration C1).
 *
 * <p>Uses neutral {@link StructuralAlertSnapshot} / {@link StructuralAlertInput} projections
 * so {@code safety} never imports {@code AlertEvent}. The {@code alert} module provides the
 * implementation and owns the {@code AlertEvent} entity mapping.</p>
 */
public interface AlertPort {

    /**
     * Open structural alerts for a building since the given instant (safety scoring input).
     */
    List<StructuralAlertSnapshot> findOpenStructuralAlerts(String buildingId, Instant since);

    /**
     * Persist a structural alert. The {@code alert} module maps the neutral input to its
     * internal {@code AlertEvent}, persists it, and returns a snapshot of the saved alert.
     *
     * @param input neutral structural-alert payload (caller sets TenantContext before calling)
     * @return snapshot of the persisted alert (id + fields the caller needs post-save)
     */
    SavedAlertSnapshot saveStructuralAlert(StructuralAlertInput input);

    /** Neutral projection used for safety scoring (severity-only). */
    record StructuralAlertSnapshot(String severity) {}

    /** Neutral input for persisting a structural alert. */
    record StructuralAlertInput(
            String tenantId,
            String sensorId,
            String module,        // always "STRUCTURAL"
            String measureType,   // e.g. sensorType
            Double value,
            Double threshold,
            String severity,      // already mapped to AlertEvent severity scale by caller
            String buildingId,
            String location,      // district
            Instant detectedAt,
            String status         // always "OPEN"
    ) {}

    /** Neutral snapshot of a persisted alert (fields {@code safety} consumes post-save). */
    record SavedAlertSnapshot(
            java.util.UUID id,
            String sensorId,
            String severity,
            String buildingId,
            String tenantId
    ) {}
}
