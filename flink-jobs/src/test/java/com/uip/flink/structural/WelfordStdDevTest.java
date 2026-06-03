package com.uip.flink.structural;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WelfordStdDev}.
 *
 * <p>Validates: convergence, cold-start protection, anomaly detection,
 * absolute floor check, pre-seed, and zero-variance handling.</p>
 */
@DisplayName("WelfordStdDev — online stddev algorithm")
class WelfordStdDevTest {

    // ─── Convergence ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("StdDev converges for uniform distribution")
    void stddev_convergesForUniformDistribution() {
        WelfordStdDev w = new WelfordStdDev();
        for (int i = 1; i <= 2000; i++) {
            w.update(i % 10); // values 0-9, true stddev ≈ 2.87
        }
        assertEquals(4.5, w.getMean(), 0.1, "Mean should be ~4.5");
        assertTrue(w.getStdDev() > 2.0, "StdDev should be > 2.0 for uniform 0-9");
        assertTrue(w.getStdDev() < 4.0, "StdDev should be < 4.0 for uniform 0-9");
    }

    @Test
    @DisplayName("StdDev converges for normal-like distribution")
    void stddev_convergesForNormalLike() {
        WelfordStdDev w = new WelfordStdDev();
        // Simulate vibration readings around mean=5.0 with some noise
        double[] values = {4.8, 5.2, 4.9, 5.1, 5.0, 4.7, 5.3, 5.0, 4.9, 5.1};
        for (int i = 0; i < 200; i++) {
            w.update(values[i % values.length]);
        }
        assertEquals(5.0, w.getMean(), 0.1);
        assertTrue(w.getStdDev() < 1.0, "Low variance data should have stddev < 1.0");
    }

    // ─── Cold Start ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cold start: isAnomaly returns false when n < 1000")
    void coldStart_returnsFalseWhenBelowMinSamples() {
        WelfordStdDev w = new WelfordStdDev();
        for (int i = 0; i < 999; i++) {
            w.update(5.0);
        }
        assertFalse(w.isAnomaly(1000.0, 10.0),
                "Should not detect anomaly during cold start (n=999)");
    }

    @Test
    @DisplayName("Cold start: isAnomaly active when n >= 1000")
    void coldStart_becomesActiveAtMinSamples() {
        WelfordStdDev w = new WelfordStdDev();
        for (int i = 0; i < 1000; i++) {
            w.update(5.0); // mean=5.0, stddev≈0
        }
        // With stddev≈0, isAnomaly returns false (sigma < 1e-10 guard)
        assertFalse(w.isAnomaly(5.0, 1.0));
    }

    // ─── Anomaly Detection ───────────────────────────────────────────────────

    @Test
    @DisplayName("Anomaly: spike above 4σ detected")
    void anomaly_detectsSpike() {
        WelfordStdDev w = new WelfordStdDev();
        // Feed 1000 values around mean=5.0 with stddev≈0.5
        for (int i = 0; i < 1000; i++) {
            w.update(5.0 + (i % 10 - 5) * 0.1);
        }
        double mean = w.getMean();
        double sigma = w.getStdDev();
        assertTrue(sigma > 0.1, "Should have non-trivial stddev, got " + sigma);

        // Spike at mean + 10*sigma should be detected (>4σ)
        double spike = mean + 10 * sigma;
        assertTrue(w.isAnomaly(spike, 1.0),
                "Spike at " + spike + " should be anomalous (mean=" + mean + ", sigma=" + sigma + ")");
    }

    @Test
    @DisplayName("Anomaly: normal value not flagged")
    void anomaly_normalValueNotFlagged() {
        WelfordStdDev w = new WelfordStdDev();
        for (int i = 0; i < 1000; i++) {
            w.update(5.0 + (i % 10 - 5) * 0.1);
        }
        // Value close to mean should NOT be anomalous
        assertFalse(w.isAnomaly(w.getMean(), 1.0),
                "Value at mean should not be anomalous");
    }

    // ─── Absolute Floor ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Absolute floor: spike above 4σ but below floor → NOT anomaly")
    void absoluteFloor_blocksBelowFloor() {
        WelfordStdDev w = new WelfordStdDev();
        for (int i = 0; i < 1000; i++) {
            w.update(0.1); // very low baseline
        }
        // Value at 100 is way above 4σ, but floor=200 blocks it
        assertFalse(w.isAnomaly(100.0, 200.0),
                "Spike above 4σ but below absolute floor should NOT be anomaly");
    }

    @Test
    @DisplayName("Absolute floor: spike above 4σ AND above floor → anomaly")
    void absoluteFloor_allowsAboveFloor() {
        WelfordStdDev w = new WelfordStdDev();
        // Use pre-seed with non-zero variance so sigma guard doesn't block
        // mean=0.1, variance=0.0001 (stddev=0.01) — 500 is ~49990σ above mean
        w.preSeed(2000, 0.1, 0.0001);
        // Value at 500 is above 4σ (>>4 * 0.01) AND above floor=200
        assertTrue(w.isAnomaly(500.0, 200.0),
                "Spike above both 4σ and floor should be anomaly");
    }

    // ─── Pre-seed ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Pre-seed: immediate anomaly detection after pre-seed")
    void preSeed_enablesImmediateDetection() {
        WelfordStdDev w = new WelfordStdDev();
        // Pre-seed with 2000 samples, mean=5.0, variance=0.25 (stddev=0.5)
        w.preSeed(2000, 5.0, 0.25);

        assertEquals(2000, w.getCount());
        assertEquals(5.0, w.getMean(), 0.001);
        assertEquals(0.5, w.getStdDev(), 0.001);

        // Spike at 5.0 + 10*0.5 = 10.0 should be detected
        assertTrue(w.isAnomaly(10.0, 1.0));
    }

    // ─── Zero Variance ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Zero variance: all same values → no anomaly")
    void zeroVariance_noAnomaly() {
        WelfordStdDev w = new WelfordStdDev();
        for (int i = 0; i < 2000; i++) {
            w.update(5.0); // all identical
        }
        assertEquals(0.0, w.getStdDev(), 1e-12);
        assertFalse(w.isAnomaly(1000.0, 1.0),
                "Zero variance should not produce anomalies (sigma guard)");
    }

    @Test
    @DisplayName("GetCount returns correct number of observations")
    void getCount_returnsCorrectValue() {
        WelfordStdDev w = new WelfordStdDev();
        assertEquals(0, w.getCount());
        w.update(1.0);
        assertEquals(1, w.getCount());
        w.update(2.0);
        assertEquals(2, w.getCount());
    }
}
