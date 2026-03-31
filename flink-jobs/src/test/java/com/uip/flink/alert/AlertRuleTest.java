package com.uip.flink.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleTest {

    @ParameterizedTest
    @CsvSource({
        "aqi, >,  150, 151, true",
        "aqi, >,  150, 150, false",
        "aqi, >=, 150, 150, true",
        "aqi, >=, 150, 149, false",
        "aqi, <,  100, 99,  true",
        "aqi, <=, 100, 100, true",
        "aqi, ==, 200, 200, true",
        "aqi, ==, 200, 199, false",
    })
    @DisplayName("AlertRule.matches() evaluates operator correctly")
    void alertRule_matches_correctOperator(String mt, String op, double threshold, double value, boolean expected) {
        AlertRule rule = new AlertRule(mt, op, threshold, "WARNING");
        assertThat(rule.matches(mt, value)).isEqualTo(expected);
    }

    @Test
    @DisplayName("AlertRule does not match different measure type")
    void alertRule_doesNotMatch_differentMeasureType() {
        AlertRule rule = new AlertRule("aqi", ">", 150, "WARNING");
        assertThat(rule.matches("pm25", 200)).isFalse();
    }

    @Test
    @DisplayName("AlertRule is case-insensitive for measure type")
    void alertRule_caseInsensitive_measureType() {
        AlertRule rule = new AlertRule("AQI", ">", 150, "WARNING");
        assertThat(rule.matches("aqi", 200)).isTrue();
    }
}
