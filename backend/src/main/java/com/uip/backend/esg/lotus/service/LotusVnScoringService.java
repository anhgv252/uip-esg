package com.uip.backend.esg.lotus.service;

import com.uip.backend.common.spi.AirQualityPort;
import com.uip.backend.esg.lotus.domain.*;
import com.uip.backend.esg.repository.EsgMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * M5-4 T06: LOTUS VN Green Building certification scoring engine.
 * Calculates certification level (Certified/Silver/Gold/Platinum) based on
 * 5 categories: Energy (EN), Water (WA), Indoor Environment Quality (IEQ),
 * Materials (MA), Site & Transport (ST).
 *
 * Scoring: each indicator 1-4 points. Total out of 100.
 * Thresholds: Certified ≥40, Silver ≥50, Gold ≥60, Platinum ≥75.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LotusVnScoringService {

    private final EsgMetricRepository esgMetricRepository;
    private final AirQualityPort airQualityPort;

    /**
     * Score a building for a given period.
     * Returns full LOTUS VN report with all 5 categories.
     */
    public LotusVnReport score(String buildingId, String tenantId, YearMonth period) {
        log.info("Scoring LOTUS VN for building={}, tenant={}, period={}", buildingId, tenantId, period);

        // Calculate period range
        Instant periodStart = period.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant periodEnd = period.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        // Score each category
        LotusCategory energyScore = scoreEnergyCategory(buildingId, tenantId, periodStart, periodEnd);
        LotusCategory waterScore = scoreWaterCategory(buildingId, tenantId, periodStart, periodEnd);
        LotusCategory ieqScore = scoreIeqCategory(buildingId, tenantId, periodStart, periodEnd);
        LotusCategory materialsScore = scoreMaterialsCategory(buildingId, tenantId);
        LotusCategory siteScore = scoreSiteCategory(buildingId, tenantId);

        // Calculate total and level
        int totalScore = LotusVnReport.calculateTotal(energyScore, waterScore, ieqScore, materialsScore, siteScore);
        LotusLevel level = LotusLevel.fromScore(totalScore);
        List<LotusIndicatorResult> allIndicators = LotusVnReport.collectIndicators(
            energyScore, waterScore, ieqScore, materialsScore, siteScore);

        log.info("LOTUS VN score for building={}: totalScore={}, level={}", buildingId, totalScore, level);

        return new LotusVnReport(
            buildingId,
            period,
            energyScore,
            waterScore,
            ieqScore,
            materialsScore,
            siteScore,
            totalScore,
            level,
            allIndicators,
            Instant.now()
        );
    }

    // ─── Energy Category (EN: max 40 points) ─────────────────────────────────

    private LotusCategory scoreEnergyCategory(String buildingId, String tenantId, 
                                               Instant start, Instant end) {
        List<LotusIndicatorResult> indicators = new ArrayList<>();

        // EN-1: Energy intensity (kWh/m²/year)
        Double totalKwh = esgMetricRepository.sumByTypeAndBuilding(
            tenantId, "ENERGY", buildingId, start, end);
        
        if (totalKwh != null && totalKwh > 0) {
            // Assume 1000 m² building area (should come from building metadata in production)
            double buildingArea = 1000.0;
            double kwhPerSqm = totalKwh / buildingArea;
            int score = scoreEnergyIntensity(kwhPerSqm);
            indicators.add(new LotusIndicatorResult(
                "EN-1",
                "Energy Intensity",
                kwhPerSqm,
                120.0,  // benchmark: 120 kWh/m²/year
                score,
                "ESG",
                true
            ));
        } else {
            indicators.add(LotusIndicatorResult.notAvailable("EN-1", "Energy Intensity"));
        }

        // EN-4: Sub-metering coverage (placeholder - requires BMS device count)
        indicators.add(LotusIndicatorResult.notAvailable("EN-4", "Sub-metering Coverage"));

        return LotusCategory.from("EN", "Energy", 40, indicators);
    }

    /**
     * EN-1: Energy intensity scoring.
     * Score 4: < 80 kWh/m², Score 3: 80-120, Score 2: 120-160, Score 1: 160-200, Score 0: >200
     */
    private int scoreEnergyIntensity(double kwhPerSqmPerYear) {
        if (kwhPerSqmPerYear < 80) return 4;
        if (kwhPerSqmPerYear < 120) return 3;
        if (kwhPerSqmPerYear < 160) return 2;
        if (kwhPerSqmPerYear < 200) return 1;
        return 0;
    }

    // ─── Water Category (WA: max 20 points) ──────────────────────────────────

    private LotusCategory scoreWaterCategory(String buildingId, String tenantId, 
                                              Instant start, Instant end) {
        List<LotusIndicatorResult> indicators = new ArrayList<>();

        // WA-1: Water consumption (L/person/day)
        Double totalM3 = esgMetricRepository.sumByTypeAndBuilding(
            tenantId, "WATER", buildingId, start, end);
        
        if (totalM3 != null && totalM3 > 0) {
            // Assume 100 occupants (should come from building metadata)
            double occupants = 100.0;
            long days = java.time.Duration.between(start, end).toDays();
            double litersPerPersonDay = (totalM3 * 1000) / (occupants * days);
            int score = scoreWaterConsumption(litersPerPersonDay);
            indicators.add(new LotusIndicatorResult(
                "WA-1",
                "Water Consumption per Capita",
                litersPerPersonDay,
                120.0,  // benchmark: 120 L/person/day
                score,
                "ESG",
                true
            ));
        } else {
            indicators.add(LotusIndicatorResult.notAvailable("WA-1", "Water Consumption"));
        }

        return LotusCategory.from("WA", "Water", 20, indicators);
    }

    /**
     * WA-1: Water consumption scoring.
     * Score 4: < 80 L, Score 3: 80-120, Score 2: 120-160, Score 1: 160-200, Score 0: >200
     */
    private int scoreWaterConsumption(double litersPerPersonDay) {
        if (litersPerPersonDay < 80) return 4;
        if (litersPerPersonDay < 120) return 3;
        if (litersPerPersonDay < 160) return 2;
        if (litersPerPersonDay < 200) return 1;
        return 0;
    }

    // ─── Indoor Environment Quality (IEQ: max 20 points) ─────────────────────

    private LotusCategory scoreIeqCategory(String buildingId, String tenantId, 
                                            Instant start, Instant end) {
        List<LotusIndicatorResult> indicators = new ArrayList<>();

        // IEQ-2: PM2.5 concentration
        Double avgPm25 = airQualityPort.findAveragePm25ByBuildingAndPeriod(
            buildingId, start, end);
        
        if (avgPm25 != null) {
            int score = scorePm25(avgPm25);
            indicators.add(new LotusIndicatorResult(
                "IEQ-2",
                "PM2.5 Concentration",
                avgPm25,
                25.0,  // benchmark: 25 µg/m³
                score,
                "AQI",
                true
            ));
        } else {
            indicators.add(LotusIndicatorResult.notAvailable("IEQ-2", "PM2.5 Concentration"));
        }

        return LotusCategory.from("IEQ", "Indoor Environment Quality", 20, indicators);
    }

    /**
     * IEQ-2: PM2.5 scoring.
     * Score 4: < 12 µg/m³, Score 3: 12-25, Score 2: 25-35, Score 1: 35-45, Score 0: >45
     */
    private int scorePm25(double pm25) {
        if (pm25 < 12) return 4;
        if (pm25 < 25) return 3;
        if (pm25 < 35) return 2;
        if (pm25 < 45) return 1;
        return 0;
    }

    // ─── Materials Category (MA: max 10 points) ──────────────────────────────

    private LotusCategory scoreMaterialsCategory(String buildingId, String tenantId) {
        List<LotusIndicatorResult> indicators = new ArrayList<>();
        
        // MA indicators require manual data input
        indicators.add(LotusIndicatorResult.notAvailable("MA-1", "Recycled Materials Usage"));
        indicators.add(LotusIndicatorResult.notAvailable("MA-2", "Local Materials"));
        
        return LotusCategory.from("MA", "Materials", 10, indicators);
    }

    // ─── Site & Transport Category (ST: max 10 points) ───────────────────────

    private LotusCategory scoreSiteCategory(String buildingId, String tenantId) {
        List<LotusIndicatorResult> indicators = new ArrayList<>();
        
        // ST indicators require manual data input
        indicators.add(LotusIndicatorResult.notAvailable("ST-1", "Site Selection"));
        indicators.add(LotusIndicatorResult.notAvailable("ST-2", "Public Transport Access"));
        
        return LotusCategory.from("ST", "Site & Transport", 10, indicators);
    }
}
