package com.uip.backend.ai.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SmartPreFilter — unit tests")
class SmartPreFilterTest {

    private SmartPreFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SmartPreFilter();
    }

    // ─── BYPASS_AI — critical types ──────────────────────────────────────────

    @Test
    @DisplayName("FLOOD_ALERT → BYPASS_AI regardless of value/threshold")
    void floodAlert_returnsBypassAi() {
        assertThat(filter.evaluate("FLOOD_ALERT", 1.0, 0.5))
                .isEqualTo(SmartPreFilter.FilterDecision.BYPASS_AI);
    }

    @Test
    @DisplayName("FIRE_ALARM → BYPASS_AI regardless of value/threshold")
    void fireAlarm_returnsBypassAi() {
        assertThat(filter.evaluate("FIRE_ALARM", 0.0, 100.0))
                .isEqualTo(SmartPreFilter.FilterDecision.BYPASS_AI);
    }

    @Test
    @DisplayName("EVACUATION → BYPASS_AI regardless of value/threshold")
    void evacuation_returnsBypassAi() {
        assertThat(filter.evaluate("EVACUATION", 50.0, 100.0))
                .isEqualTo(SmartPreFilter.FilterDecision.BYPASS_AI);
    }

    // ─── HANDLE_RULE_BASED — clear threshold violation ───────────────────────

    @Test
    @DisplayName("value=200, threshold=100 → ratio=1.0 > 0.5 → HANDLE_RULE_BASED")
    void clearViolationDouble_returnsRuleBased() {
        assertThat(filter.evaluate("AQI", 200.0, 100.0))
                .isEqualTo(SmartPreFilter.FilterDecision.HANDLE_RULE_BASED);
    }

    @Test
    @DisplayName("value=0, threshold=100 → ratio=1.0 > 0.5 → HANDLE_RULE_BASED")
    void valueBelowByHalf_returnsRuleBased() {
        assertThat(filter.evaluate("WATER_LEVEL", 0.0, 100.0))
                .isEqualTo(SmartPreFilter.FilterDecision.HANDLE_RULE_BASED);
    }

    @Test
    @DisplayName("value=160, threshold=100 → ratio=0.6 > 0.5 → HANDLE_RULE_BASED")
    void ratioJustAboveHalf_returnsRuleBased() {
        assertThat(filter.evaluate("NOISE", 160.0, 100.0))
                .isEqualTo(SmartPreFilter.FilterDecision.HANDLE_RULE_BASED);
    }

    // ─── ESCALATE_AI — close to threshold ────────────────────────────────────

    @Test
    @DisplayName("value=105, threshold=100 → ratio=0.05 ≤ 0.5 → ESCALATE_AI")
    void closeToThreshold_returnsEscalateAi() {
        assertThat(filter.evaluate("AQI", 105.0, 100.0))
                .isEqualTo(SmartPreFilter.FilterDecision.ESCALATE_AI);
    }

    @Test
    @DisplayName("value=148, threshold=100 → ratio=0.48 ≤ 0.5 → ESCALATE_AI (borderline)")
    void borderlineBelow_returnsEscalateAi() {
        assertThat(filter.evaluate("HUMIDITY", 148.0, 100.0))
                .isEqualTo(SmartPreFilter.FilterDecision.ESCALATE_AI);
    }

    @Test
    @DisplayName("value equals threshold → ratio=0.0 → ESCALATE_AI")
    void exactlyAtThreshold_returnsEscalateAi() {
        assertThat(filter.evaluate("TEMPERATURE", 100.0, 100.0))
                .isEqualTo(SmartPreFilter.FilterDecision.ESCALATE_AI);
    }

    // ─── isCriticalBypass ────────────────────────────────────────────────────

    @Test
    @DisplayName("isCriticalBypass(FIRE_ALARM) → true")
    void isCriticalBypass_fireAlarm_returnsTrue() {
        assertThat(filter.isCriticalBypass("FIRE_ALARM")).isTrue();
    }

    @Test
    @DisplayName("isCriticalBypass(FLOOD_ALERT) → true")
    void isCriticalBypass_floodAlert_returnsTrue() {
        assertThat(filter.isCriticalBypass("FLOOD_ALERT")).isTrue();
    }

    @Test
    @DisplayName("isCriticalBypass(AQI) → false")
    void isCriticalBypass_aqi_returnsFalse() {
        assertThat(filter.isCriticalBypass("AQI")).isFalse();
    }

    // ─── zero threshold guard ─────────────────────────────────────────────────

    @Test
    @DisplayName("threshold=0, value=5 → no division by zero, ratio=5.0 > 0.5 → HANDLE_RULE_BASED")
    void zeroThreshold_noDivisionByZero() {
        assertThat(filter.evaluate("STRUCTURAL", 5.0, 0.0))
                .isEqualTo(SmartPreFilter.FilterDecision.HANDLE_RULE_BASED);
    }
}
