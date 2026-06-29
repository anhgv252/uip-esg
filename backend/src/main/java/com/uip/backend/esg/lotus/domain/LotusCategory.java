package com.uip.backend.esg.lotus.domain;

import java.util.List;

/**
 * M5-4 T06: LOTUS VN category score (Energy, Water, IEQ, Materials, Site).
 */
public record LotusCategory(
    String code,                        // EN, WA, IEQ, MA, ST
    String name,
    int score,                          // sum of indicator scores in this category
    int maxScore,                       // maximum possible score for this category
    List<LotusIndicatorResult> indicators
) {
    /**
     * Calculate category score from indicators.
     */
    public static LotusCategory from(String code, String name, int maxScore, List<LotusIndicatorResult> indicators) {
        int totalScore = indicators.stream()
            .mapToInt(LotusIndicatorResult::score)
            .sum();
        return new LotusCategory(code, name, totalScore, maxScore, indicators);
    }
}
