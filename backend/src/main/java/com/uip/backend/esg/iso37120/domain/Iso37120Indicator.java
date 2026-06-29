package com.uip.backend.esg.iso37120.domain;

/**
 * M5-4 T10: Single ISO 37120:2018 indicator.
 * Example: E1 = "Total residential electrical energy use per capita" (kWh/capita).
 */
public record Iso37120Indicator(
    String code,            // E1, E2, Env1, T1, W1, G1, etc.
    String name,
    String category,        // Energy, Environment, Transport, Waste, Governance
    double value,
    String unit,            // kWh/capita, µg/m³, %, tons/capita
    String dataSource,      // "ESG", "AQI", "MANUAL", "NOT_AVAILABLE"
    boolean dataAvailable
) {
    /**
     * Create indicator with no data available.
     */
    public static Iso37120Indicator notAvailable(String code, String name, String category, String unit) {
        return new Iso37120Indicator(code, name, category, 0.0, unit, "NOT_AVAILABLE", false);
    }
}
