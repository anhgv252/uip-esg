package com.uip.backend.ai.flink;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * M4-AI-01: Configuration for Flink district-level aggregation job.
 *
 * <p>Controls tumbling window size, per-district sensor ceiling, and output Kafka topic.
 * Defaults target the 5-minute data freshness SLA for district dashboards.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "ai.flink.district")
@Data
public class DistrictAggregationConfig {

    /** Tumbling window duration in seconds. Default: 300 (5-minute windows). */
    private int batchSizeSeconds = 300;

    /** Maximum sensors to include per district in one aggregation batch. Default: 500. */
    private int maxSensorsPerDistrict = 500;

    /** Kafka topic where aggregated district results are published. */
    private String outputTopic = "ai.district.aggregations";
}
