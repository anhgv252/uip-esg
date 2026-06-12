package com.uip.backend.ai.flink;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DistrictAggregationConfig} — verifies default values and custom property binding.
 */
@DisplayName("DistrictAggregationConfig")
class DistrictAggregationConfigTest {

    // ─── Default Values ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Default values (no properties set)")
    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = DistrictAggregationConfigTest.DefaultsTestConfig.class)
    class DefaultValuesTest {

        @Autowired
        private DistrictAggregationConfig config;

        @Test
        @DisplayName("batchSizeSeconds defaults to 300 (5-minute tumbling window)")
        void batchSizeSeconds_default() {
            assertThat(config.getBatchSizeSeconds()).isEqualTo(300);
        }

        @Test
        @DisplayName("maxSensorsPerDistrict defaults to 500")
        void maxSensorsPerDistrict_default() {
            assertThat(config.getMaxSensorsPerDistrict()).isEqualTo(500);
        }

        @Test
        @DisplayName("outputTopic defaults to ai.district.aggregations")
        void outputTopic_default() {
            assertThat(config.getOutputTopic()).isEqualTo("ai.district.aggregations");
        }
    }

    // ─── Custom Values ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Custom values via @TestPropertySource")
    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = DistrictAggregationConfigTest.DefaultsTestConfig.class)
    @TestPropertySource(properties = {
            "ai.flink.district.batch-size-seconds=60",
            "ai.flink.district.max-sensors-per-district=100",
            "ai.flink.district.output-topic=test.district.output"
    })
    class CustomValuesTest {

        @Autowired
        private DistrictAggregationConfig config;

        @Test
        @DisplayName("batchSizeSeconds bound to custom value 60")
        void batchSizeSeconds_customValue() {
            assertThat(config.getBatchSizeSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("maxSensorsPerDistrict bound to custom value 100")
        void maxSensorsPerDistrict_customValue() {
            assertThat(config.getMaxSensorsPerDistrict()).isEqualTo(100);
        }

        @Test
        @DisplayName("outputTopic bound to test.district.output")
        void outputTopic_customValue() {
            assertThat(config.getOutputTopic()).isEqualTo("test.district.output");
        }
    }

    // ─── Shared test configuration ────────────────────────────────────────────

    @Configuration
    @EnableConfigurationProperties(DistrictAggregationConfig.class)
    static class DefaultsTestConfig {
        // Minimal Spring context: only binds @ConfigurationProperties
    }
}
