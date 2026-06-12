package com.uip.backend.correlation.flink;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * M4-COR-01: Configuration for Flink CEP correlation window.
 *
 * <p>Bound from {@code correlation.flink.*} properties with safe defaults
 * so the application starts without external config in development.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "correlation.flink")
@Data
public class IncidentCorrelationConfig {

    /** CEP window width in seconds — events within this window are candidates for correlation. */
    private int windowSeconds = 30;

    /** Minimum number of distinct sensor types required to form a correlated incident. */
    private int minSensorTypes = 3;

    /** Minimum correlation score [0.0, 1.0] to persist an incident. */
    private double minCorrelationScore = 0.6;

    /** Kafka topic where correlated incident events are published. */
    private String outputTopic = "correlated.incidents";

    /** Safety cap on events ingested per window to avoid runaway memory usage. */
    private int maxEventsPerWindow = 100;
}
