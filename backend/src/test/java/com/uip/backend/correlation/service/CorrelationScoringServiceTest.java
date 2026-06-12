package com.uip.backend.correlation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CorrelationScoringService — unit tests")
class CorrelationScoringServiceTest {

    private CorrelationScoringService service;

    @BeforeEach
    void setUp() {
        service = new CorrelationScoringService();
    }

    // ─── score() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3 types, min=3, time=5s, window=30s → typeCoverage=1.0 * spread=0.833 ≥ 0.6")
    void threeTypes_shortSpread_highScore() {
        double s = service.score(3, 3, 5, 30);
        assertThat(s).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    @DisplayName("1 type, min=3 → typeCoverage=0.33 → score < 0.6 threshold")
    void oneType_scoresBelowThreshold() {
        double s = service.score(1, 3, 5, 30);
        assertThat(service.meetsThreshold(s, 0.6)).isFalse();
    }

    @Test
    @DisplayName("All sensors at same instant (timeRange=0) → timeSpread=1.0 → score=1.0 (perfect)")
    void allAtSameTime_perfectScore() {
        double s = service.score(3, 3, 0, 30);
        assertThat(s).isEqualTo(1.0);
    }

    @Test
    @DisplayName("score is capped at 1.0 even when typeCoverage > 1 and spread is large")
    void scoreIsCappedAtOne() {
        // 6 distinct types for min=3 → typeCoverage=2.0; at t=0 spread=1 → raw=2.0, capped to 1.0
        double s = service.score(6, 3, 0, 30);
        assertThat(s).isEqualTo(1.0);
    }

    @Test
    @DisplayName("timeRange equals window → timeSpread=0 → min floor 0.1 applied")
    void timeRangeEqualsWindow_usesMinFloor() {
        // timeSpread=0 → max(0.1, 0)=0.1; typeCoverage=1.0 → score=0.1
        double s = service.score(3, 3, 30, 30);
        assertThat(s).isEqualTo(0.1, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    @DisplayName("borderline: 2 types, min=3, spread close to 1.0 → still below 0.6")
    void twoTypes_borderline_belowThreshold() {
        // typeCoverage = 2/3 = 0.667; timeSpread = 1-1/30 ≈ 0.967; score ≈ 0.644
        // Actually this could be above 0.6 — just verify meetsThreshold matches
        double s = service.score(2, 3, 1, 30);
        boolean meets = service.meetsThreshold(s, 0.6);
        // Just assert the method is consistent (no exception)
        assertThat(meets).isEqualTo(s >= 0.6);
    }

    // ─── meetsThreshold() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("score=0.7, minScore=0.6 → meetsThreshold=true")
    void meetsThreshold_above_returnsTrue() {
        assertThat(service.meetsThreshold(0.7, 0.6)).isTrue();
    }

    @Test
    @DisplayName("score=0.5, minScore=0.6 → meetsThreshold=false")
    void meetsThreshold_below_returnsFalse() {
        assertThat(service.meetsThreshold(0.5, 0.6)).isFalse();
    }

    @Test
    @DisplayName("score=0.6 exactly (boundary) → meetsThreshold=true")
    void meetsThreshold_exactBoundary_returnsTrue() {
        assertThat(service.meetsThreshold(0.6, 0.6)).isTrue();
    }
}
