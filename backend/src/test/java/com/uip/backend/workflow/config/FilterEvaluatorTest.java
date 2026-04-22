package com.uip.backend.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FilterEvaluator")
class FilterEvaluatorTest {

    private FilterEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new FilterEvaluator(new ObjectMapper());
    }

    // ─── EQ ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EQ — match when value equals")
    void eq_match() {
        String json = "[{\"field\":\"module\",\"op\":\"EQ\",\"value\":\"ENVIRONMENT\"}]";
        assertThat(evaluator.matches(json, Map.of("module", "ENVIRONMENT"))).isTrue();
    }

    @Test
    @DisplayName("EQ — no match when value differs")
    void eq_noMatch() {
        String json = "[{\"field\":\"module\",\"op\":\"EQ\",\"value\":\"ENVIRONMENT\"}]";
        assertThat(evaluator.matches(json, Map.of("module", "TRAFFIC"))).isFalse();
    }

    // ─── GT ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GT — match when actual > threshold")
    void gt_match() {
        String json = "[{\"field\":\"value\",\"op\":\"GT\",\"value\":150.0}]";
        assertThat(evaluator.matches(json, Map.of("value", 175.0))).isTrue();
    }

    @Test
    @DisplayName("GT — no match when actual <= threshold")
    void gt_noMatch() {
        String json = "[{\"field\":\"value\",\"op\":\"GT\",\"value\":150.0}]";
        assertThat(evaluator.matches(json, Map.of("value", 100.0))).isFalse();
    }

    // ─── GTE / LT / LTE ─────────────────────────────────────────────────

    @Test
    @DisplayName("GTE — match when equal")
    void gte_equal() {
        String json = "[{\"field\":\"value\",\"op\":\"GTE\",\"value\":150.0}]";
        assertThat(evaluator.matches(json, Map.of("value", 150.0))).isTrue();
    }

    @Test
    @DisplayName("LT — match when actual < threshold")
    void lt_match() {
        String json = "[{\"field\":\"value\",\"op\":\"LT\",\"value\":3.5}]";
        assertThat(evaluator.matches(json, Map.of("value", 2.0))).isTrue();
    }

    @Test
    @DisplayName("LTE — match when equal")
    void lte_equal() {
        String json = "[{\"field\":\"value\",\"op\":\"LTE\",\"value\":3.5}]";
        assertThat(evaluator.matches(json, Map.of("value", 3.5))).isTrue();
    }

    // ─── IN ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IN — match when value in list")
    void in_match() {
        String json = "[{\"field\":\"measureType\",\"op\":\"IN\",\"value\":[\"WATER_LEVEL\"]}]";
        assertThat(evaluator.matches(json, Map.of("measureType", "WATER_LEVEL"))).isTrue();
    }

    @Test
    @DisplayName("IN — no match when value not in list")
    void in_noMatch() {
        String json = "[{\"field\":\"measureType\",\"op\":\"IN\",\"value\":[\"WATER_LEVEL\"]}]";
        assertThat(evaluator.matches(json, Map.of("measureType", "AQI"))).isFalse();
    }

    // ─── Multiple AND conditions ─────────────────────────────────────────

    @Test
    @DisplayName("Multiple conditions — all must match (AND)")
    void multipleConditions_and() {
        String json = "[{\"field\":\"module\",\"op\":\"EQ\",\"value\":\"ENVIRONMENT\"},{\"field\":\"measureType\",\"op\":\"EQ\",\"value\":\"AQI\"},{\"field\":\"value\",\"op\":\"GT\",\"value\":150.0}]";
        Map<String, Object> payload = Map.of("module", "ENVIRONMENT", "measureType", "AQI", "value", 175.0);
        assertThat(evaluator.matches(json, payload)).isTrue();
    }

    @Test
    @DisplayName("Multiple conditions — one fails → overall false")
    void multipleConditions_oneFails() {
        String json = "[{\"field\":\"module\",\"op\":\"EQ\",\"value\":\"ENVIRONMENT\"},{\"field\":\"value\",\"op\":\"GT\",\"value\":150.0}]";
        Map<String, Object> payload = Map.of("module", "ENVIRONMENT", "value", 100.0);
        assertThat(evaluator.matches(json, payload)).isFalse();
    }

    // ─── Null / missing field ────────────────────────────────────────────

    @Test
    @DisplayName("IS_NULL — true when field missing")
    void isNull_missing() {
        String json = "[{\"field\":\"foo\",\"op\":\"IS_NULL\"}]";
        assertThat(evaluator.matches(json, Map.of())).isTrue();
    }

    @Test
    @DisplayName("IS_NOT_NULL — true when field present")
    void isNotNull_present() {
        String json = "[{\"field\":\"module\",\"op\":\"IS_NOT_NULL\"}]";
        assertThat(evaluator.matches(json, Map.of("module", "ENVIRONMENT"))).isTrue();
    }

    // ─── Null filter / empty filter ──────────────────────────────────────

    @Test
    @DisplayName("Null filter → matches everything")
    void nullFilter_matchesAll() {
        assertThat(evaluator.matches(null, Map.of("any", "thing"))).isTrue();
    }

    @Test
    @DisplayName("Empty filter → matches everything")
    void emptyFilter_matchesAll() {
        assertThat(evaluator.matches("", Map.of("any", "thing"))).isTrue();
    }

    // ─── Invalid JSON ────────────────────────────────────────────────────

    @Test
    @DisplayName("Invalid JSON → returns false")
    void invalidJson_returnsFalse() {
        assertThat(evaluator.matches("not json", Map.of("any", "thing"))).isFalse();
    }

    // ─── NE ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("NE — match when value differs")
    void ne_match() {
        String json = "[{\"field\":\"module\",\"op\":\"NE\",\"value\":\"FLOOD\"}]";
        assertThat(evaluator.matches(json, Map.of("module", "ENVIRONMENT"))).isTrue();
    }

    // ─── CONTAINS ────────────────────────────────────────────────────────

    @Test
    @DisplayName("CONTAINS — match when string contains substring")
    void contains_match() {
        String json = "[{\"field\":\"description\",\"op\":\"CONTAINS\",\"value\":\"factory\"}]";
        assertThat(evaluator.matches(json, Map.of("description", "Bad smell near factory"))).isTrue();
    }
}
