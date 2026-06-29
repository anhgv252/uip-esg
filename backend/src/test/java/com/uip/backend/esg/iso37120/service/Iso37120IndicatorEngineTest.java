package com.uip.backend.esg.iso37120.service;

import com.uip.backend.esg.iso37120.domain.Iso37120Indicator;
import com.uip.backend.esg.iso37120.domain.Iso37120Report;
import com.uip.backend.esg.repository.EsgMetricRepository;
import com.uip.backend.environment.repository.AirQualityReadingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * M5-4 T10: Unit tests for ISO 37120:2018 City services and quality of life indicators.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Iso37120IndicatorEngine")
class Iso37120IndicatorEngineTest {

    private static final String CITY_ID = "hcm";
    private static final String TENANT_ID = "hcm";
    private static final int YEAR = 2026;

    @Mock private EsgMetricRepository esgMetricRepository;
    @Mock private AirQualityReadingRepository airQualityRepository;

    @InjectMocks private Iso37120IndicatorEngine indicatorEngine;

    // ─── All 15 Indicators Present ───────────────────────────────────────────

    @Test
    @DisplayName("generate() returns report with all 9 indicator codes")
    void generate_allIndicatorsPresent() {
        when(esgMetricRepository.sumByTypeAndRange(any(), any(), any(), any()))
            .thenReturn(1_000_000.0);
        when(airQualityRepository.findAveragePm25ByPeriod(any(), any(), any()))
            .thenReturn(15.0);

        Iso37120Report report = indicatorEngine.generate(CITY_ID, TENANT_ID, YEAR);

        assertThat(report).isNotNull();
        assertThat(report.indicators()).hasSize(9);
        assertThat(report.indicators())
            .extracting(Iso37120Indicator::code)
            .containsExactlyInAnyOrder("E1", "E2", "Env1", "Env2", "Env3", "T1", "W1", "G1", "G2");
        assertThat(report.cityId()).isEqualTo(CITY_ID);
        assertThat(report.year()).isEqualTo(YEAR);
        assertThat(report.calculatedAt()).isNotNull();
    }

    // ─── E2 Renewable Energy Percentage ──────────────────────────────────────

    @Test
    @DisplayName("generate() calculates E2 renewable energy percentage correctly")
    void generate_e2_renewableEnergyPercentage() {
        when(esgMetricRepository.sumByTypeAndRange(any(), any(), any(), any()))
            .thenReturn(1_000_000.0);
        when(airQualityRepository.findAveragePm25ByPeriod(any(), any(), any()))
            .thenReturn(null);

        Iso37120Report report = indicatorEngine.generate(CITY_ID, TENANT_ID, YEAR);

        Iso37120Indicator e2 = report.indicators().stream()
            .filter(i -> i.code().equals("E2"))
            .findFirst()
            .orElseThrow();

        assertThat(e2.category()).isEqualTo("Energy");
        assertThat(e2.value()).isEqualTo(95.0);
        assertThat(e2.unit()).isEqualTo("%");
        assertThat(e2.dataAvailable()).isTrue();
        assertThat(e2.dataSource()).isEqualTo("MANUAL");
    }

    // ─── Env1 PM2.5 from AQI Sensor ──────────────────────────────────────────

    @Test
    @DisplayName("generate() calculates Env1 PM2.5 from AQI sensor data")
    void generate_env1_pm25FromAqi() {
        when(esgMetricRepository.sumByTypeAndRange(any(), any(), any(), any()))
            .thenReturn(null);
        when(airQualityRepository.findAveragePm25ByPeriod(eq(TENANT_ID), any(Instant.class), any(Instant.class)))
            .thenReturn(22.5);

        Iso37120Report report = indicatorEngine.generate(CITY_ID, TENANT_ID, YEAR);

        Iso37120Indicator env1 = report.indicators().stream()
            .filter(i -> i.code().equals("Env1"))
            .findFirst()
            .orElseThrow();

        assertThat(env1.name()).isEqualTo("Fine particulate matter (PM2.5) concentration");
        assertThat(env1.category()).isEqualTo("Environment");
        assertThat(env1.value()).isEqualTo(22.5);
        assertThat(env1.unit()).isEqualTo("µg/m³");
        assertThat(env1.dataAvailable()).isTrue();
        assertThat(env1.dataSource()).isEqualTo("AQI");
    }

    // ─── Null Handling for Missing Data ──────────────────────────────────────

    @Test
    @DisplayName("generate() handles null data sources gracefully with NOT_AVAILABLE indicators")
    void generate_nullHandling_gracefulFallback() {
        when(esgMetricRepository.sumByTypeAndRange(any(), any(), any(), any()))
            .thenReturn(null);
        when(airQualityRepository.findAveragePm25ByPeriod(any(), any(), any()))
            .thenReturn(null);

        Iso37120Report report = indicatorEngine.generate(CITY_ID, TENANT_ID, YEAR);

        // E1, Env1, Env2, W1 should be NOT_AVAILABLE
        assertThat(report.indicators())
            .filteredOn(i -> i.code().equals("E1") || i.code().equals("Env1") || 
                           i.code().equals("Env2") || i.code().equals("W1"))
            .allMatch(i -> !i.dataAvailable())
            .allMatch(i -> i.dataSource().equals("NOT_AVAILABLE"));

        // E2 is MANUAL, should still be available
        Iso37120Indicator e2 = report.indicators().stream()
            .filter(i -> i.code().equals("E2"))
            .findFirst()
            .orElseThrow();
        assertThat(e2.dataAvailable()).isTrue();
    }

    // ─── Year Defaults to Current ────────────────────────────────────────────

    @Test
    @DisplayName("generate() uses provided year in report")
    void generate_yearInReport() {
        when(esgMetricRepository.sumByTypeAndRange(any(), any(), any(), any()))
            .thenReturn(500_000.0);
        when(airQualityRepository.findAveragePm25ByPeriod(any(), any(), any()))
            .thenReturn(18.0);

        Iso37120Report report = indicatorEngine.generate(CITY_ID, TENANT_ID, YEAR);

        assertThat(report.year()).isEqualTo(YEAR);
    }

    // ─── Report JSON Structure ───────────────────────────────────────────────

    @Test
    @DisplayName("generate() returns JSON-serializable report with grouped categories")
    void generate_jsonStructure_groupedCategories() {
        when(esgMetricRepository.sumByTypeAndRange(any(), any(), any(), any()))
            .thenReturn(1_500_000.0);
        when(airQualityRepository.findAveragePm25ByPeriod(any(), any(), any()))
            .thenReturn(12.5);

        Iso37120Report report = indicatorEngine.generate(CITY_ID, TENANT_ID, YEAR);

        // Verify groupedByCategory() helper
        var grouped = report.groupedByCategory();
        assertThat(grouped).containsKeys("Energy", "Environment", "Transport", "Waste", "Governance");
        assertThat(grouped.get("Energy")).hasSize(2);  // E1, E2
        assertThat(grouped.get("Environment")).hasSize(3);  // Env1, Env2, Env3
        assertThat(grouped.get("Waste")).hasSize(1);  // W1
        assertThat(grouped.get("Governance")).hasSize(2);  // G1, G2

        // Verify availableCount() helper
        long available = report.availableCount();
        assertThat(available).isGreaterThanOrEqualTo(3);  // At least E1, E2, Env1 with data
    }
}
