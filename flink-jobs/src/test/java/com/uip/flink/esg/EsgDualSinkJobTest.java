package com.uip.flink.esg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EsgDualSinkJobTest {

    // ── extractBuildingId ──────────────────────────────────────────────────

    @ParameterizedTest(name = "deviceId={0} → buildingId={1}")
    @CsvSource({
        "SENSOR-PERF-BLD-001-001, PERF-BLD-001",
        "SENSOR-PERF-BLD-002-042, PERF-BLD-002",
        "SENSOR-BLD-001-099,      BLD-001",
        "BLD-001-SENSOR-01,       BLD-001",
        "UNKNOWN_DEVICE,          ''",
        ",                        ''"
    })
    void extractBuildingId_parsesKnownPatterns(String deviceId, String expected) {
        assertThat(EsgDualSinkJob.extractBuildingId(deviceId)).isEqualTo(expected);
    }

    @Test
    void extractBuildingId_nullReturnsEmpty() {
        assertThat(EsgDualSinkJob.extractBuildingId(null)).isEmpty();
    }

    // Exactly-once (kill Flink mid-batch → restart → no dup CH) yêu cầu Flink cluster running.
    // Deferred to Sprint 2. Sprint 1 scope: EXACTLY_ONCE mode + .uid() configured in source.
}
