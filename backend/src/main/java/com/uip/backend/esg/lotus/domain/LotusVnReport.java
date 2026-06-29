package com.uip.backend.esg.lotus.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * M5-4 T06: LOTUS VN Green Building Certification Report.
 * Total score out of 100 from 5 categories: EN (40), WA (20), IEQ (20), MA (10), ST (10).
 */
public record LotusVnReport(
    String buildingId,
    YearMonth period,
    LotusCategory energyScore,         // EN: max 40 points
    LotusCategory waterScore,          // WA: max 20 points
    LotusCategory ieqScore,            // IEQ: max 20 points
    LotusCategory materialsScore,      // MA: max 10 points
    LotusCategory siteScore,           // ST: max 10 points
    int totalScore,                    // sum of all category scores
    LotusLevel certificationLevel,     // PLATINUM/GOLD/SILVER/CERTIFIED/NOT_CERTIFIED
    List<LotusIndicatorResult> indicators,
    Instant calculatedAt
) {
    /**
     * Calculate total score from all categories.
     */
    public static int calculateTotal(LotusCategory energy, LotusCategory water, LotusCategory ieq, 
                                      LotusCategory materials, LotusCategory site) {
        return energy.score() + water.score() + ieq.score() + materials.score() + site.score();
    }

    /**
     * Collect all indicators from all categories.
     */
    public static List<LotusIndicatorResult> collectIndicators(LotusCategory energy, LotusCategory water, 
                                                                 LotusCategory ieq, LotusCategory materials, 
                                                                 LotusCategory site) {
        return List.of(
            energy.indicators(),
            water.indicators(),
            ieq.indicators(),
            materials.indicators(),
            site.indicators()
        ).stream()
        .flatMap(List::stream)
        .toList();
    }
}
