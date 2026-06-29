package com.uip.backend.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * M5-3 T06: ROI Dashboard response for a single building.
 * 
 * Contains:
 * - Cost breakdown (base fee, AI overage, total)
 * - Savings calculation (manual ops vs UIP)
 * - Comparison metrics (before/after)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Building ROI response with cost breakdown and savings")
public class BuildingRoiResponse {

    @Schema(description = "Building identifier", example = "BLDG-001")
    private String buildingId;

    @Schema(description = "Month of analysis (YYYY-MM)", example = "2026-06")
    private String month;

    @Schema(description = "Cost breakdown for the month")
    private CostBreakdown costs;

    @Schema(description = "Savings calculation (manual ops vs UIP)")
    private SavingsBreakdown savings;

    @Schema(description = "Comparison chart data (before vs after UIP)")
    private List<ComparisonMetric> comparisonChart;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostBreakdown {
        @Schema(description = "Base subscription fee (VND)", example = "2000000")
        private Long baseFeeVnd;

        @Schema(description = "Total AI tokens used", example = "125000")
        private Long aiTokensUsed;

        @Schema(description = "AI tokens above base allocation", example = "25000")
        private Long aiOverageTokens;

        @Schema(description = "AI overage cost (VND)", example = "1250")
        private Long aiOverageCostVnd;

        @Schema(description = "Total monthly cost (VND)", example = "2001250")
        private Long totalCostVnd;

        @Schema(description = "Number of sensor readings ingested", example = "8640")
        private Long sensorReadings;

        @Schema(description = "Number of alerts generated", example = "14")
        private Long alertsGenerated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavingsBreakdown {
        @Schema(description = "Manual operations cost baseline (VND/month)", example = "8500000")
        private Long manualOpsCostVnd;

        @Schema(description = "Net savings (manual - UIP cost) (VND)", example = "6498750")
        private Long automationSavingsVnd;

        @Schema(description = "Payback period (months)", example = "3.1")
        private BigDecimal paybackMonths;

        @Schema(description = "CO2 emissions saved (kg)", example = "42.5")
        private BigDecimal co2SavedKg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonMetric {
        @Schema(description = "Metric name", example = "Energy (kWh)")
        private String metric;

        @Schema(description = "Value before UIP", example = "15200")
        private Long before;

        @Schema(description = "Value after UIP", example = "13850")
        private Long after;

        @Schema(description = "Unit of measurement", example = "kWh")
        private String unit;
    }
}
