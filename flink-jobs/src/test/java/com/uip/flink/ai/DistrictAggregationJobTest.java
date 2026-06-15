package com.uip.flink.ai;

import com.uip.flink.common.NgsiLdMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4-AI-01: Unit tests for {@link DistrictAggregationJob} helpers and
 * {@link DistrictAggregationFunction} aggregation logic.
 *
 * <p>Pure-POJO tests (no MiniCluster) following the
 * {@code VibrationAnomalyJobTest} convention.</p>
 */
@DisplayName("DistrictAggregationJob — district-level batching")
class DistrictAggregationJobTest {

    // ─── Filter ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Filter: message with district + value passes")
    void filter_validMessage_passes() {
        NgsiLdMessage msg = message("HCM-D1", "AQI", 95.0, "sensor-1");
        assertThat(DistrictAggregationJob.hasDistrictAndValue(msg)).isTrue();
    }

    @Test
    @DisplayName("Filter: null message rejected")
    void filter_nullMessage_rejected() {
        assertThat(DistrictAggregationJob.hasDistrictAndValue(null)).isFalse();
    }

    @Test
    @DisplayName("Filter: missing/blank district rejected")
    void filter_missingDistrict_rejected() {
        NgsiLdMessage noDistrict = message(null, "AQI", 95.0, "sensor-1");
        NgsiLdMessage blankDistrict = message("  ", "AQI", 95.0, "sensor-1");
        assertThat(DistrictAggregationJob.hasDistrictAndValue(noDistrict)).isFalse();
        assertThat(DistrictAggregationJob.hasDistrictAndValue(blankDistrict)).isFalse();
    }

    @Test
    @DisplayName("Filter: message without numeric value rejected")
    void filter_missingValue_rejected() {
        NgsiLdMessage msg = new NgsiLdMessage();
        NgsiLdMessage.Meta meta = new NgsiLdMessage.Meta();
        meta.setDistrict("HCM-D1");
        meta.setSensorType("AQI");
        msg.setMeta(meta);
        // measurements empty -> no "value"
        assertThat(DistrictAggregationJob.hasDistrictAndValue(msg)).isFalse();
    }

    // ─── Key extraction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Key: composite (tenant, district, sensorType) extracted")
    void key_compositeExtracted() {
        NgsiLdMessage msg = message("HCM-D1", "AQI", 95.0, "sensor-1");
        msg.getMeta().setTenantId("tenant-A");
        DistrictKey key = DistrictAggregationJob.extractKey(msg);
        assertThat(key.getTenantId()).isEqualTo("tenant-A");
        assertThat(key.getDistrictCode()).isEqualTo("HCM-D1");
        assertThat(key.getSensorType()).isEqualTo("AQI");
    }

    @Test
    @DisplayName("Key: unknown sensorType falls back to UNKNOWN")
    void key_missingSensorType_fallsBack() {
        NgsiLdMessage msg = message("HCM-D1", null, 95.0, "sensor-1");
        DistrictKey key = DistrictAggregationJob.extractKey(msg);
        assertThat(key.getSensorType()).isEqualTo("UNKNOWN");
    }

    // ─── Aggregation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Aggregate: count, max, avg computed over 3 readings")
    void aggregate_threeReadings_correctStats() {
        DistrictAggregationFunction fn = new DistrictAggregationFunction(500);
        DistrictAggregationFunction.Accumulator acc = fn.createAccumulator();
        acc = fn.add(message("HCM-D1", "AQI", 80.0, "s1"), acc);
        acc = fn.add(message("HCM-D1", "AQI", 120.0, "s2"), acc);
        acc = fn.add(message("HCM-D1", "AQI", 100.0, "s3"), acc);

        assertThat(acc.count).isEqualTo(3);
        assertThat(acc.max).isEqualTo(120.0);
        assertThat(acc.sum / acc.count).isEqualTo(100.0); // avg
        assertThat(acc.snapshots).hasSize(3);
    }

    @Test
    @DisplayName("Aggregate: readings without value are skipped")
    void aggregate_valuelessReadings_skipped() {
        DistrictAggregationFunction fn = new DistrictAggregationFunction(500);
        DistrictAggregationFunction.Accumulator acc = fn.createAccumulator();
        acc = fn.add(message("HCM-D1", "AQI", 80.0, "s1"), acc);
        // second reading has empty measurements
        NgsiLdMessage noValue = new NgsiLdMessage();
        NgsiLdMessage.Meta meta = new NgsiLdMessage.Meta();
        meta.setDistrict("HCM-D1");
        meta.setSensorType("AQI");
        noValue.setMeta(meta);
        acc = fn.add(noValue, acc);

        assertThat(acc.count).isEqualTo(1);
    }

    @Test
    @DisplayName("Aggregate: snapshot list capped at maxSensors (FIFO eviction)")
    void aggregate_snapshotCapped_fifoEviction() {
        DistrictAggregationFunction fn = new DistrictAggregationFunction(2);
        DistrictAggregationFunction.Accumulator acc = fn.createAccumulator();
        acc = fn.add(message("HCM-D1", "AQI", 80.0, "s1"), acc);
        acc = fn.add(message("HCM-D1", "AQI", 90.0, "s2"), acc);
        acc = fn.add(message("HCM-D1", "AQI", 100.0, "s3"), acc); // evicts s1

        assertThat(acc.snapshots).hasSize(2);
        assertThat(acc.snapshots.get(0).getSensorId()).isEqualTo("s2");
        assertThat(acc.snapshots.get(1).getSensorId()).isEqualTo("s3");
        assertThat(acc.count).isEqualTo(3); // count still reflects all 3
    }

    @Test
    @DisplayName("Aggregate: merge combines stats from two partials")
    void aggregate_merge_combines() {
        DistrictAggregationFunction fn = new DistrictAggregationFunction(500);
        DistrictAggregationFunction.Accumulator a = fn.createAccumulator();
        a = fn.add(message("HCM-D1", "AQI", 80.0, "s1"), a);
        DistrictAggregationFunction.Accumulator b = fn.createAccumulator();
        b = fn.add(message("HCM-D1", "AQI", 120.0, "s2"), b);

        DistrictAggregationFunction.Accumulator merged = fn.merge(a, b);
        assertThat(merged.count).isEqualTo(2);
        assertThat(merged.max).isEqualTo(120.0);
        assertThat(merged.snapshots).hasSize(2);
    }

    // ─── DTO serialization ───────────────────────────────────────────────────

    @Test
    @DisplayName("DTO: DistrictAggregation is Serializable")
    void dto_isSerializable() {
        DistrictAggregation agg = new DistrictAggregation(
                "tenant-A", "HCM-D1", "AQI", 3, 120.0, 100.0,
                1_700_000_000_000L, 1_700_000_060_000L,
                List.of(new DistrictAggregation.SensorSnapshot("s1", 80.0, 1L)));
        assertThat(agg).isInstanceOf(java.io.Serializable.class);
        assertThat(agg.getCount()).isEqualTo(3);
        assertThat(agg.getSensors()).hasSize(1);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static NgsiLdMessage message(String district, String sensorType, double value, String deviceId) {
        NgsiLdMessage msg = new NgsiLdMessage();
        NgsiLdMessage.Meta meta = new NgsiLdMessage.Meta();
        meta.setDistrict(district);
        meta.setSensorType(sensorType);
        msg.setMeta(meta);

        NgsiLdMessage.NgsiLdProperty<String> dev = new NgsiLdMessage.NgsiLdProperty<>();
        dev.setValue(deviceId);
        msg.setDeviceId(dev);

        NgsiLdMessage.NgsiLdProperty<Map<String, Double>> meas = new NgsiLdMessage.NgsiLdProperty<>();
        meas.setValue(Map.of("value", value));
        msg.setMeasurements(meas);

        NgsiLdMessage.NgsiLdProperty<Long> at = new NgsiLdMessage.NgsiLdProperty<>();
        at.setValue(System.currentTimeMillis());
        msg.setObservedAt(at);
        return msg;
    }
}
