package com.uip.backend.ai.anomaly;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * M4-AI-07: Configuration properties for the Welford universal anomaly detector.
 *
 * <p>Bound from the {@code app.welford} prefix in application.yml / environment.
 * Defaults are suitable for production:
 * <ul>
 *   <li>{@code learningPhaseSize = 100} — readings before anomaly detection activates</li>
 *   <li>{@code sensitivityMultiplier = 2.0} — sigma multiplier (2σ = 95.4% confidence band)</li>
 * </ul>
 * </p>
 *
 * <p>Override example in {@code application.yml}:
 * <pre>
 * app:
 *   welford:
 *     learning-phase-size: 200
 *     sensitivity-multiplier: 2.5
 * </pre>
 * </p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.welford")
public class WelfordConfig {

    /**
     * Number of readings to accumulate before anomaly detection activates
     * (learning / warm-up phase). Default: 100.
     */
    private int learningPhaseSize = 100;

    /**
     * Sigma multiplier for the anomaly threshold.
     * A value of 2.0 means readings beyond ±2σ from the running mean are flagged.
     * Default: 2.0 (≈95.4% confidence band).
     */
    private double sensitivityMultiplier = 2.0;
}
