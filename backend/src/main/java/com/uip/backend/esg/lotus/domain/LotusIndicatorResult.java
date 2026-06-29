package com.uip.backend.esg.lotus.domain;

/**
 * M5-4 T06: Result for a single LOTUS VN indicator.
 */
public record LotusIndicatorResult(
    String code,              // EN-1, WA-1, IEQ-2, etc.
    String name,
    double actualValue,       // actual measured value
    double benchmarkValue,    // benchmark/threshold for scoring
    int score,                // 0-4 points
    String dataSource,        // "BMS", "ESG", "AQI", "MANUAL", "NOT_AVAILABLE"
    boolean dataAvailable     // false if no data source
) {
    /**
     * Create indicator result with no data available.
     */
    public static LotusIndicatorResult notAvailable(String code, String name) {
        return new LotusIndicatorResult(
            code,
            name,
            0.0,
            0.0,
            0,
            "NOT_AVAILABLE",
            false
        );
    }
}
