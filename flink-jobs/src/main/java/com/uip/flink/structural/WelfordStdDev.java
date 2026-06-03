package com.uip.flink.structural;

import java.io.Serializable;

/**
 * Welford online algorithm for computing running mean and standard deviation.
 *
 * <p>Used by {@link VibrationAnomalyJob} to maintain per-sensor baseline statistics
 * in Flink keyed state. The 4-sigma rule detects statistical anomalies while
 * the cold-start guard (n &lt; 1000) prevents false alerts during initialization.</p>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Welford's_online_algorithm">Welford's algorithm</a>
 */
public class WelfordStdDev implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Minimum samples before anomaly detection is active (cold-start protection). */
    static final long MIN_SAMPLES = 1000;

    private long n = 0;
    private double mean = 0.0;
    private double m2 = 0.0;

    /** Update the running statistics with a new observation. */
    public void update(double x) {
        n++;
        double delta = x - mean;
        mean += delta / n;
        double delta2 = x - mean;
        m2 += delta * delta2;
    }

    /** Current running mean. */
    public double getMean() {
        return mean;
    }

    /** Current sample-corrected standard deviation. Returns 0 if n &lt; 2. */
    public double getStdDev() {
        return n < 2 ? 0.0 : Math.sqrt(m2 / (n - 1));
    }

    /** Number of observations processed so far. */
    public long getCount() {
        return n;
    }

    /**
     * Check whether the given value is anomalous using the 4-sigma rule.
     *
     * <p>An anomaly is declared when:</p>
     * <ol>
     *   <li>At least {@link #MIN_SAMPLES} observations have been processed (cold-start guard)</li>
     *   <li>The standard deviation is non-trivial (&gt; 1e-10)</li>
     *   <li>{@code |x - mean| > 4 * stddev} (statistical anomaly)</li>
     *   <li>{@code x > absoluteFloor} (regulatory minimum threshold, e.g. TCVN 9386:2012)</li>
     * </ol>
     *
     * @param x             the observed value
     * @param absoluteFloor the regulatory minimum threshold (WARNING level)
     * @return true if the value is considered anomalous
     */
    public boolean isAnomaly(double x, double absoluteFloor) {
        if (n < MIN_SAMPLES) return false;
        double sigma = getStdDev();
        if (sigma < 1e-10) return false;
        return Math.abs(x - mean) > 4 * sigma && x > absoluteFloor;
    }

    /**
     * Pre-seed the Welford state from historical data.
     * Useful for reducing cold-start period on Flink job restart.
     *
     * @param count    number of historical observations
     * @param mean     historical mean
     * @param variance historical sample variance (not M2)
     */
    public void preSeed(long count, double mean, double variance) {
        this.n = count;
        this.mean = mean;
        this.m2 = variance * (count - 1);
    }
}
