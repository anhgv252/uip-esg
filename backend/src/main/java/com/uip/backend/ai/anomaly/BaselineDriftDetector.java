package com.uip.backend.ai.anomaly;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M4-COR-05: Detects AQI baseline drift over a 7-day rolling window.
 *
 * <p>Each sensor has an independent {@link Deque} of timestamped readings kept in
 * insertion order. On every {@link #addReading} call old entries outside the 7-day
 * window are evicted to cap memory usage.</p>
 *
 * <p>Drift is declared when the 7-day rolling mean rises more than 10% above the
 * externally supplied {@code currentBaseline} (typically today's moving average).</p>
 *
 * <p>Thread safety: {@link ConcurrentHashMap} protects concurrent sensor updates.
 * Individual deque mutations are serialized per sensor key via
 * {@link ConcurrentHashMap#compute}.</p>
 */
@Component
@Slf4j
public class BaselineDriftDetector {

    /** A single timestamped AQI reading stored in the rolling window. */
    private record TimestampedReading(double value, Instant timestamp) {}

    /**
     * Result returned when drift is detected.
     *
     * @param sensorId     sensor that drifted
     * @param oldBaseline  the {@code currentBaseline} supplied by the caller
     * @param newBaseline  the 7-day rolling mean that exceeded the threshold
     * @param driftPercent fractional drift {@code (newBaseline - oldBaseline) / oldBaseline}
     */
    public record DriftEvent(
            String sensorId,
            double oldBaseline,
            double newBaseline,
            double driftPercent
    ) {}

    // ─── Config ───────────────────────────────────────────────────────────────

    /** Rolling window size — 7 calendar days. */
    static final Duration WINDOW          = Duration.ofDays(7);

    /** Minimum fractional rise (above baseline) to trigger drift detection: 10%. */
    static final double  DRIFT_THRESHOLD  = 0.10;

    // ─── State ────────────────────────────────────────────────────────────────

    /**
     * Per-sensor deque of timestamped readings.
     * Key format: sensor identifier string (e.g. "AQI:building-01").
     */
    private final Map<String, Deque<TimestampedReading>> readings = new ConcurrentHashMap<>();

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Adds a new reading to the 7-day rolling window for {@code sensorId} and evicts
     * any entries older than 7 days relative to {@code timestamp}.
     *
     * @param sensorId  unique sensor identifier
     * @param value     AQI reading to record
     * @param timestamp observation time of this reading
     */
    public void addReading(String sensorId, double value, Instant timestamp) {
        readings.compute(sensorId, (key, deque) -> {
            if (deque == null) {
                deque = new ArrayDeque<>();
            }
            deque.addLast(new TimestampedReading(value, timestamp));
            evictOldEntries(deque, timestamp);
            return deque;
        });
        log.trace("[BaselineDrift] sensorId={} value={} ts={} windowSize={}",
                sensorId, value, timestamp, readings.get(sensorId).size());
    }

    /**
     * Checks whether the 7-day rolling mean for {@code sensorId} has risen more than
     * {@value DRIFT_THRESHOLD} (10%) above {@code currentBaseline}.
     *
     * @param sensorId        sensor to evaluate
     * @param currentBaseline today's reference baseline (e.g. 24-hour moving average)
     * @return a {@link DriftEvent} if drift is detected, or {@link Optional#empty()}
     *         if readings are insufficient or drift is within tolerance
     */
    public Optional<DriftEvent> checkDrift(String sensorId, double currentBaseline) {
        Deque<TimestampedReading> deque = readings.get(sensorId);
        if (deque == null || deque.isEmpty()) {
            return Optional.empty();
        }

        double rollingMean = deque.stream()
                .mapToDouble(TimestampedReading::value)
                .average()
                .orElse(0.0);

        double threshold = currentBaseline * (1.0 + DRIFT_THRESHOLD);

        if (rollingMean > threshold) {
            double driftPercent = currentBaseline > 0
                    ? (rollingMean - currentBaseline) / currentBaseline
                    : 0.0;
            log.warn("[BaselineDrift] Drift detected: sensorId={} baseline={} rollingMean={} drift={}%",
                    sensorId, currentBaseline, rollingMean, String.format("%.1f", driftPercent * 100));
            return Optional.of(new DriftEvent(sensorId, currentBaseline, rollingMean, driftPercent));
        }

        return Optional.empty();
    }

    /**
     * Computes an adjusted alert threshold that accounts for measured baseline drift.
     *
     * <p>Formula: {@code adjustedThreshold = originalThreshold * (1 + driftPercent)}</p>
     *
     * @param originalThreshold the threshold configured when the sensor was first deployed
     * @param driftPercent      fractional drift (e.g. 0.15 for 15% drift)
     * @return adjusted threshold scaled by the drift factor
     */
    public double adjustThreshold(double originalThreshold, double driftPercent) {
        return originalThreshold * (1.0 + driftPercent);
    }

    /**
     * Returns the number of readings currently held in the rolling window for a sensor.
     * Intended for observability and testing.
     *
     * @param sensorId sensor identifier
     * @return window size (0 if sensor is unknown)
     */
    public int windowSize(String sensorId) {
        Deque<TimestampedReading> deque = readings.get(sensorId);
        return deque == null ? 0 : deque.size();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Removes entries from the front of {@code deque} that are strictly older than
     * {@code now - WINDOW}.
     */
    private void evictOldEntries(Deque<TimestampedReading> deque, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        // Deque is in insertion order (oldest first); remove from front until cutoff
        while (!deque.isEmpty() && deque.peekFirst().timestamp().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }
}
