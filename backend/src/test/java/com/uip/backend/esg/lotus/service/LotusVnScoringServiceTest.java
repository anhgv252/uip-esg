package com.uip.backend.esg.lotus.service;

import com.uip.backend.esg.lotus.domain.*;
import com.uip.backend.esg.repository.EsgMetricRepository;
import com.uip.backend.common.spi.AirQualityPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * M5-4 T06: Unit tests for LOTUS VN Green Building certification scoring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LotusVnScoringService")
class LotusVnScoringServiceTest {

    private static final String BUILDING_ID = "BUILDING-01";
    private static final String TENANT_ID = "hcm";
    private static final YearMonth PERIOD = YearMonth.of(2026, 6);

    @Mock private EsgMetricRepository esgMetricRepository;
    @Mock private AirQualityPort airQualityPort;

    @InjectMocks private LotusVnScoringService scoringService;

    // ─── Platinum Level (≥75 points) ─────────────────────────────────────────

    @Test
    @DisplayName("score() achieves Platinum level (75+) with excellent performance")
    void score_platinum_excellentPerformance() {
        // EN-1: 50 kWh/m² → 4 points (< 80 threshold)
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("ENERGY"), eq(BUILDING_ID), any(), any()))
            .thenReturn(50_000.0);  // 50 kWh/m² × 1000 m²

        // WA-1: 60 L/person/day → 4 points (< 80 threshold)
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("WATER"), eq(BUILDING_ID), any(), any()))
            .thenReturn(180.0);  // 60 L × 100 people × 30 days / 1000

        // IEQ-2: 10 µg/m³ → 4 points (< 12 threshold)
        when(airQualityPort.findAveragePm25ByBuildingAndPeriod(eq(BUILDING_ID), any(), any()))
            .thenReturn(10.0);

        LotusVnReport report = scoringService.score(BUILDING_ID, TENANT_ID, PERIOD);

        assertThat(report).isNotNull();
        assertThat(report.totalScore()).isGreaterThanOrEqualTo(75);
        assertThat(report.certificationLevel()).isEqualTo(LotusLevel.PLATINUM);
        assertThat(report.buildingId()).isEqualTo(BUILDING_ID);
        assertThat(report.period()).isEqualTo(PERIOD);
    }

    // ─── Certified Level (≥40 points) ────────────────────────────────────────

    @Test
    @DisplayName("score() achieves Certified level (40-49) with baseline performance")
    void score_certified_baselinePerformance() {
        // EN-1: 180 kWh/m² → 1 point
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("ENERGY"), eq(BUILDING_ID), any(), any()))
            .thenReturn(180_000.0);

        // WA-1: 180 L/person/day → 1 point
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("WATER"), eq(BUILDING_ID), any(), any()))
            .thenReturn(540.0);

        // IEQ-2: 40 µg/m³ → 1 point
        when(airQualityPort.findAveragePm25ByBuildingAndPeriod(eq(BUILDING_ID), any(), any()))
            .thenReturn(40.0);

        LotusVnReport report = scoringService.score(BUILDING_ID, TENANT_ID, PERIOD);

        assertThat(report.totalScore()).isBetween(40, 49);
        assertThat(report.certificationLevel()).isEqualTo(LotusLevel.CERTIFIED);
    }

    // ─── NOT_CERTIFIED (<40 points) ──────────────────────────────────────────

    @Test
    @DisplayName("score() returns NOT_CERTIFIED when total score < 40")
    void score_notCertified_poorPerformance() {
        // EN-1: 250 kWh/m² → 0 points (>200 threshold)
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("ENERGY"), eq(BUILDING_ID), any(), any()))
            .thenReturn(250_000.0);

        // WA-1: 250 L/person/day → 0 points
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("WATER"), eq(BUILDING_ID), any(), any()))
            .thenReturn(750.0);

        // IEQ-2: 60 µg/m³ → 0 points (>45 threshold)
        when(airQualityPort.findAveragePm25ByBuildingAndPeriod(eq(BUILDING_ID), any(), any()))
            .thenReturn(60.0);

        LotusVnReport report = scoringService.score(BUILDING_ID, TENANT_ID, PERIOD);

        assertThat(report.totalScore()).isLessThan(40);
        assertThat(report.certificationLevel()).isEqualTo(LotusLevel.NOT_CERTIFIED);
    }

    // ─── Individual Indicators ───────────────────────────────────────────────

    @Test
    @DisplayName("score() calculates EN-1 energy intensity correctly")
    void score_en1_energyIntensity() {
        // 100 kWh/m² → score 3 (80-120 range)
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("ENERGY"), eq(BUILDING_ID), any(), any()))
            .thenReturn(100_000.0);
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("WATER"), eq(BUILDING_ID), any(), any()))
            .thenReturn(null);
        when(airQualityPort.findAveragePm25ByBuildingAndPeriod(any(), any(), any()))
            .thenReturn(null);

        LotusVnReport report = scoringService.score(BUILDING_ID, TENANT_ID, PERIOD);

        LotusIndicatorResult en1 = report.indicators().stream()
            .filter(i -> i.code().equals("EN-1"))
            .findFirst()
            .orElseThrow();

        assertThat(en1.dataAvailable()).isTrue();
        assertThat(en1.actualValue()).isEqualTo(100.0);
        assertThat(en1.score()).isEqualTo(3);
        assertThat(en1.dataSource()).isEqualTo("ESG");
    }

    @Test
    @DisplayName("score() calculates WA-1 water consumption correctly")
    void score_wa1_waterConsumption() {
        // 150 L/person/day → score 2 (120-160 range)
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("ENERGY"), eq(BUILDING_ID), any(), any()))
            .thenReturn(null);
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("WATER"), eq(BUILDING_ID), any(), any()))
            .thenReturn(450.0);  // 150 L × 100 people × 30 days / 1000
        when(airQualityPort.findAveragePm25ByBuildingAndPeriod(any(), any(), any()))
            .thenReturn(null);

        LotusVnReport report = scoringService.score(BUILDING_ID, TENANT_ID, PERIOD);

        LotusIndicatorResult wa1 = report.indicators().stream()
            .filter(i -> i.code().equals("WA-1"))
            .findFirst()
            .orElseThrow();

        assertThat(wa1.dataAvailable()).isTrue();
        assertThat(wa1.actualValue()).isEqualTo(150.0);
        assertThat(wa1.score()).isEqualTo(2);
        assertThat(wa1.dataSource()).isEqualTo("ESG");
    }

    @Test
    @DisplayName("score() calculates IEQ-2 PM2.5 correctly")
    void score_ieq2_pm25() {
        // 20 µg/m³ → score 3 (12-25 range)
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("ENERGY"), eq(BUILDING_ID), any(), any()))
            .thenReturn(null);
        when(esgMetricRepository.sumByTypeAndBuilding(eq(TENANT_ID), eq("WATER"), eq(BUILDING_ID), any(), any()))
            .thenReturn(null);
        when(airQualityPort.findAveragePm25ByBuildingAndPeriod(eq(BUILDING_ID), any(), any()))
            .thenReturn(20.0);

        LotusVnReport report = scoringService.score(BUILDING_ID, TENANT_ID, PERIOD);

        LotusIndicatorResult ieq2 = report.indicators().stream()
            .filter(i -> i.code().equals("IEQ-2"))
            .findFirst()
            .orElseThrow();

        assertThat(ieq2.dataAvailable()).isTrue();
        assertThat(ieq2.actualValue()).isEqualTo(20.0);
        assertThat(ieq2.score()).isEqualTo(3);
        assertThat(ieq2.dataSource()).isEqualTo("AQI");
    }

    // ─── Missing Data Handling ───────────────────────────────────────────────

    @Test
    @DisplayName("score() handles missing data gracefully with NOT_AVAILABLE indicators")
    void score_missingData_gracefulFallback() {
        // All data sources return null
        when(esgMetricRepository.sumByTypeAndBuilding(any(), any(), any(), any(), any()))
            .thenReturn(null);
        when(airQualityPort.findAveragePm25ByBuildingAndPeriod(any(), any(), any()))
            .thenReturn(null);

        LotusVnReport report = scoringService.score(BUILDING_ID, TENANT_ID, PERIOD);

        assertThat(report).isNotNull();
        assertThat(report.totalScore()).isEqualTo(0);
        assertThat(report.certificationLevel()).isEqualTo(LotusLevel.NOT_CERTIFIED);
        assertThat(report.indicators()).allMatch(i -> !i.dataAvailable());
    }

    // ─── Report Structure ────────────────────────────────────────────────────

    @Test
    @DisplayName("score() returns complete report structure with all 5 categories")
    void score_reportStructure_allCategoriesPresent() {
        when(esgMetricRepository.sumByTypeAndBuilding(any(), any(), any(), any(), any()))
            .thenReturn(100_000.0);
        when(airQualityPort.findAveragePm25ByBuildingAndPeriod(any(), any(), any()))
            .thenReturn(15.0);

        LotusVnReport report = scoringService.score(BUILDING_ID, TENANT_ID, PERIOD);

        assertThat(report.energyScore()).isNotNull();
        assertThat(report.waterScore()).isNotNull();
        assertThat(report.ieqScore()).isNotNull();
        assertThat(report.materialsScore()).isNotNull();
        assertThat(report.siteScore()).isNotNull();
        assertThat(report.energyScore().code()).isEqualTo("EN");
        assertThat(report.waterScore().code()).isEqualTo("WA");
        assertThat(report.ieqScore().code()).isEqualTo("IEQ");
        assertThat(report.materialsScore().code()).isEqualTo("MA");
        assertThat(report.siteScore().code()).isEqualTo("ST");
        assertThat(report.calculatedAt()).isNotNull();
    }
}
