package com.uip.backend.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AqiCalculator")
class AqiCalculatorTest {

    private AqiCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new AqiCalculator();
    }

    // ─── calculateAqi: all-null returns null ─────────────────────────────────

    @Test
    @DisplayName("returns null when all pollutants are null")
    void calculateAqi_allNull_returnsNull() {
        assertThat(calculator.calculateAqi(null, null, null, null, null, null)).isNull();
    }

    // ─── PM2.5 sub-index boundary checks ──────────────────────────────────────

    @ParameterizedTest(name = "PM2.5={0} → AQI≈{1}")
    @CsvSource({
        "0.0,  0",    // bottom of Good bracket
        "12.0, 50",   // top of Good bracket
        "12.1, 51",   // bottom of Moderate
        "35.4, 100",  // top of Moderate
        "35.5, 101",  // USG
        "55.4, 150",  // top of USG
        "55.5, 151",  // Unhealthy
        "150.4, 200", // top of Unhealthy
    })
    @DisplayName("PM2.5 boundary values map to expected AQI")
    void pm25_boundaries(double pm25, int expectedAqi) {
        Integer result = calculator.calculateAqi(pm25, null, null, null, null, null);
        assertThat(result).isNotNull();
        // Allow ±1 rounding tolerance
        assertThat(result).isBetween(expectedAqi - 1, expectedAqi + 1);
    }

    // ─── PM10 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PM10=54 → AQI=50 (Good)")
    void pm10_good_boundary() {
        Integer aqi = calculator.calculateAqi(null, 54.0, null, null, null, null);
        assertThat(aqi).isBetween(49, 51);
    }

    @Test
    @DisplayName("PM10=155 → AQI=101 (USG)")
    void pm10_usg_lower_boundary() {
        Integer aqi = calculator.calculateAqi(null, 155.0, null, null, null, null);
        assertThat(aqi).isBetween(100, 102);
    }

    // ─── max of multiple pollutants ───────────────────────────────────────────

    @Test
    @DisplayName("overall AQI is max of individual sub-indices")
    void calculateAqi_returnsMax() {
        // PM2.5=5 → AQI≈21, PM10=200 → AQI≈127 → max should be ~127
        Integer aqi = calculator.calculateAqi(5.0, 200.0, null, null, null, null);
        assertThat(aqi).isGreaterThan(100);
    }

    // ─── concentration above max breakpoint ────────────────────────────────────

    @Test
    @DisplayName("concentration above top breakpoint returns 500")
    void calculateAqi_aboveMaxBreakpoint_returns500() {
        Integer aqi = calculator.calculateAqi(600.0, null, null, null, null, null);
        assertThat(aqi).isEqualTo(500);
    }

    // ─── categoryLabel ─────────────────────────────────────────────────────────

    @ParameterizedTest(name = "AQI={0} → {1}")
    @CsvSource({
        "0,   Good",
        "50,  Good",
        "51,  Moderate",
        "100, Moderate",
        "101, Unhealthy for Sensitive Groups",
        "150, Unhealthy for Sensitive Groups",
        "151, Unhealthy",
        "200, Unhealthy",
        "201, Very Unhealthy",
        "300, Very Unhealthy",
        "301, Hazardous",
        "500, Hazardous",
    })
    @DisplayName("categoryLabel maps AQI to correct label")
    void categoryLabel_boundaries(int aqi, String expected) {
        assertThat(calculator.categoryLabel(aqi)).isEqualTo(expected);
    }

    // ─── categoryColor ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("categoryColor returns EPA green for Good AQI")
    void categoryColor_good() {
        assertThat(calculator.categoryColor(25)).isEqualTo("#00E400");
    }

    @Test
    @DisplayName("categoryColor returns maroon for Hazardous AQI")
    void categoryColor_hazardous() {
        assertThat(calculator.categoryColor(400)).isEqualTo("#7E0023");
    }
}
