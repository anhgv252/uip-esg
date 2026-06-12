package com.uip.backend.correlation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * M4-COR-01: Computes a correlation score for a set of sensor events that occurred
 * within a sliding time window.
 *
 * <p>Score formula:
 * <pre>
 *   typeCoverage  = distinctTypes / minRequired
 *   timeSpread    = 1 - (timeRangeSeconds / windowSeconds)   // higher when events cluster together
 *   score         = min(1.0,  typeCoverage * max(0.1, timeSpread))
 * </pre>
 * A score of 1.0 means all required sensor types fired simultaneously.
 * Scores below {@code minCorrelationScore} are discarded by the CEP pipeline.</p>
 */
@Service
@Slf4j
public class CorrelationScoringService {

    /**
     * Calculate a correlation score.
     *
     * @param distinctTypes    number of distinct sensor types in the window
     * @param minRequired      minimum sensor types needed for a valid incident
     * @param timeRangeSeconds spread between first and last event in the window
     * @param windowSeconds    total window duration
     * @return score in [0.0, 1.0]
     */
    public double score(int distinctTypes, int minRequired, long timeRangeSeconds, int windowSeconds) {
        double typeCoverage = (double) distinctTypes / minRequired;
        double timeSpread = 1.0 - ((double) timeRangeSeconds / windowSeconds);
        double computed = typeCoverage * Math.max(0.1, timeSpread);
        double result = Math.min(1.0, computed);
        log.debug("[CorrelationScoring] distinct={} min={} tRange={}s window={}s score={}",
                distinctTypes, minRequired, timeRangeSeconds, windowSeconds, result);
        return result;
    }

    /**
     * Returns {@code true} when the given score meets the configured minimum threshold.
     *
     * @param score    computed correlation score
     * @param minScore configured minimum (e.g. 0.6)
     */
    public boolean meetsThreshold(double score, double minScore) {
        return score >= minScore;
    }
}
