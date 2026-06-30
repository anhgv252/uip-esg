package com.uip.backend.safety.service;

import com.uip.backend.common.spi.AlertPort;
import com.uip.backend.safety.dto.VibrationReadingResponse;
import com.uip.backend.safety.model.SafetyScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * Computes and caches the safety score (0-100) for a building based on
 * active structural alerts in the last 24 hours.
 *
 * <p>Score formula: {@code 100 - (CRITICAL × 30) - (WARNING × 10)}, clamped to [0, 100].</p>
 *
 * <p><strong>BR-010:</strong> This score is for operator awareness only.
 * No automated action (evacuation, shutdown) is taken based on this score.</p>
 *
 * <p>Cache key: {@code tenantId:buildingId} with 5-minute TTL.
 * Evicted by {@link #evictSafetyScore(String)} when a new structural alert arrives.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingSafetyService {

    private static final int SCORE_MAX   = 100;
    private static final int CRITICAL_PENALTY = 30;
    private static final int WARNING_PENALTY  = 10;
    private static final long LOOKBACK_HOURS  = 24;

    private final AlertPort alertPort;

    /**
     * Returns the current safety score for a building, using Redis cache (TTL 5 min).
     * Cache key includes tenantId for RLS-equivalent isolation.
     */
    @Cacheable(
            value = "safety:score",
            key = "T(com.uip.backend.tenant.context.TenantContext).getCurrentTenant() + ':' + #buildingId"
    )
    public SafetyScore getSafetyScore(String buildingId) {
        Instant since = Instant.now().minus(LOOKBACK_HOURS, ChronoUnit.HOURS);
        List<AlertPort.StructuralAlertSnapshot> alerts = alertPort.findOpenStructuralAlerts(buildingId, since);
        return computeScore(buildingId, alerts);
    }

    /**
     * Evicts the cached safety score for a building.
     * Called by StructuralAlertConsumer (B2-5) after a new P0 alert is persisted.
     */
    @CacheEvict(
            value = "safety:score",
            key = "T(com.uip.backend.tenant.context.TenantContext).getCurrentTenant() + ':' + #buildingId"
    )
    public void evictSafetyScore(String buildingId) {
        log.debug("Safety score cache evicted for building={}", buildingId);
    }

    /**
     * Returns structural sensor readings for a building in the requested time range.
     * Returns empty list until the structural sensor readings pipeline (B2-5/B2-6) is active
     * and structural sensors are registered for this building.
     *
     * @param buildingId  target building
     * @param sensorType  e.g. STRUCTURAL_VIBRATION
     * @param range       e.g. "24h", "7d"
     */
    public List<VibrationReadingResponse> getVibrationReadings(
            String buildingId, String sensorType, String range) {
        // Structural sensor readings pipeline (B2-5/B2-6) is not yet active.
        // Returns empty until structural sensors are registered and raw_payload extraction
        // is implemented via SensorReadingRepository native query.
        return Collections.emptyList();
    }

    // ─── Score algorithm ─────────────────────────────────────────────────────

    public static SafetyScore computeScore(String buildingId, List<AlertPort.StructuralAlertSnapshot> alerts) {
        long criticalCount = alerts.stream()
                .filter(a -> "CRITICAL".equals(a.severity()))
                .count();
        long warningCount = alerts.stream()
                .filter(a -> "WARNING".equals(a.severity()))
                .count();

        int score = (int) (SCORE_MAX - criticalCount * CRITICAL_PENALTY - warningCount * WARNING_PENALTY);
        score = Math.max(0, Math.min(SCORE_MAX, score));

        String status;
        if (criticalCount > 0)  status = "CRITICAL";
        else if (warningCount > 0) status = "WARNING";
        else status = "SAFE";

        return new SafetyScore(buildingId, score, status, Instant.now(), (int) (criticalCount + warningCount));
    }
}
