package com.uip.backend.esg.lotus.domain;

/**
 * LOTUS VN certification levels based on total score.
 * M5-4 T06: LOTUS VN Green Building Certification Engine.
 */
public enum LotusLevel {
    PLATINUM(75, "Platinum - Highest level of green building performance"),
    GOLD(60, "Gold - Advanced green building performance"),
    SILVER(50, "Silver - Intermediate green building performance"),
    CERTIFIED(40, "Certified - Basic green building performance"),
    NOT_CERTIFIED(0, "Not Certified - Below minimum threshold");

    private final int minScore;
    private final String description;

    LotusLevel(int minScore, String description) {
        this.minScore = minScore;
        this.description = description;
    }

    public int getMinScore() {
        return minScore;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Determine certification level from total score.
     * Score thresholds: Platinum ≥75, Gold ≥60, Silver ≥50, Certified ≥40.
     */
    public static LotusLevel fromScore(int totalScore) {
        if (totalScore >= PLATINUM.minScore) return PLATINUM;
        if (totalScore >= GOLD.minScore) return GOLD;
        if (totalScore >= SILVER.minScore) return SILVER;
        if (totalScore >= CERTIFIED.minScore) return CERTIFIED;
        return NOT_CERTIFIED;
    }
}
