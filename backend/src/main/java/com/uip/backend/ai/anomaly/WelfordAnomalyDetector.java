package com.uip.backend.ai.anomaly;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M4-AI-07: Universal anomaly detector using Welford's online algorithm for
 * incremental mean and variance estimation.
 *
 * <p>Supports any sensor type (AQI, water level, noise, humidity, temperature,
 * structural) via a generic string key. Each sensor maintains independent state
 * so sensors never interfere with each other.</p>
 *
 * <p>Algorithm:
 * <ol>
 *   <li>For the first {@code learningPhaseCount} readings, accumulate statistics without raising anomalies.</li>
 *   <li>After the learning phase, flag readings whose z-score exceeds {@code sigmaThreshold}.</li>
 * </ol>
 * </p>
 *
 * <p>Thread safety: {@link ConcurrentHashMap} protects concurrent sensor updates.
 * Individual {@link WelfordState} records are immutable; each update produces a new instance.</p>
 */
@Component
@Slf4j
public class WelfordAnomalyDetector {

    /** Immutable snapshot of Welford online statistics for one sensor key. */
    public record WelfordState(long count, double mean, double m2) {

        /** Initial empty state for a newly seen sensor. */
        public static WelfordState empty() {
            return new WelfordState(0, 0, 0);
        }

        /** Returns {@code true} while still in the learning phase. */
        public boolean isLearning(int learningPhaseCount) {
            return count < learningPhaseCount;
        }

        /**
         * Sample standard deviation (Bessel-corrected).
         * Returns {@code 0} when fewer than 2 samples are available.
         */
        public double stdDev() {
            return count < 2 ? 0 : Math.sqrt(m2 / (count - 1));
        }
    }

    /**
     * Sensor types supported by this detector — informational, not enforced at runtime.
     * The detector accepts any string key, so new types can be used without enum changes.
     */
    public enum SensorType {
        AQI, WATER_LEVEL, NOISE, HUMIDITY, TEMPERATURE, STRUCTURAL,
        /** M4-AI-07: mechanical / seismic vibration sensor */
        VIBRATION,
        /** M4-AI-07: smoke / particulate matter sensor */
        SMOKE,
        /** M4-AI-07: barometric or differential pressure sensor */
        PRESSURE,
        /** M4-AI-07: carbon monoxide level sensor */
        CO_LEVEL
    }

    // ─── Config ───────────────────────────────────────────────────────────────

    @Value("${ai.welford.learning-phase-count:100}")
    private int learningPhaseCount;

    @Value("${ai.welford.sigma-threshold:3.0}")
    private double sigmaThreshold;

    // ─── State ────────────────────────────────────────────────────────────────

    private final Map<String, WelfordState> states = new ConcurrentHashMap<>();

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Incorporate {@code value} into the running statistics for {@code sensorKey}
     * and return the updated state.
     *
     * @param sensorKey unique key identifying a sensor (e.g. "AQI:building-01")
     * @param value     new reading to incorporate
     * @return updated {@link WelfordState} after incorporating the value
     */
    public WelfordState update(String sensorKey, double value) {
        WelfordState prev = states.getOrDefault(sensorKey, WelfordState.empty());
        long n = prev.count() + 1;
        double delta = value - prev.mean();
        double newMean = prev.mean() + delta / n;
        double delta2 = value - newMean;
        double newM2 = prev.m2() + delta * delta2;
        WelfordState next = new WelfordState(n, newMean, newM2);
        states.put(sensorKey, next);
        log.trace("[Welford] sensorKey={} n={} mean={} stdDev={}", sensorKey, n, newMean, next.stdDev());
        return next;
    }

    /**
     * Returns {@code true} when {@code value} is anomalous for {@code sensorKey}.
     *
     * <p>Returns {@code false} during the learning phase or when no statistics exist yet.</p>
     *
     * @param sensorKey unique sensor identifier
     * @param value     reading to evaluate
     */
    public boolean isAnomaly(String sensorKey, double value) {
        WelfordState state = states.get(sensorKey);
        if (state == null || state.isLearning(learningPhaseCount)) {
            return false;
        }
        double std = state.stdDev() == 0 ? 1 : state.stdDev();
        double zScore = Math.abs(value - state.mean()) / std;
        boolean anomaly = zScore > sigmaThreshold;
        if (anomaly) {
            log.warn("[Welford] Anomaly detected: sensorKey={} value={} mean={} std={} zScore={}",
                    sensorKey, value, state.mean(), std, zScore);
        }
        return anomaly;
    }

    /**
     * Returns {@code true} when the sensor is still in the learning phase
     * (fewer than {@code learningPhaseCount} readings accumulated).
     */
    public boolean isInLearningPhase(String sensorKey) {
        WelfordState state = states.get(sensorKey);
        return state == null || state.isLearning(learningPhaseCount);
    }
}
