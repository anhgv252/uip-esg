package com.uip.backend.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * M5-3 T06: Tenant-level ROI summary aggregating all buildings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tenant ROI summary across all buildings")
public class TenantRoiSummary {

    @Schema(description = "Tenant identifier", example = "TENANT-001")
    private String tenantId;

    @Schema(description = "Month of analysis (YYYY-MM)", example = "2026-06")
    private String month;

    @Schema(description = "Number of buildings in tenant", example = "5")
    private Integer buildingCount;

    @Schema(description = "Total costs (VND)")
    private Long totalCostVnd;

    @Schema(description = "Total savings (VND)")
    private Long totalSavingsVnd;

    @Schema(description = "Average payback period (months)")
    private BigDecimal avgPaybackMonths;

    @Schema(description = "Total CO2 saved (kg)")
    private BigDecimal totalCo2SavedKg;

    @Schema(description = "Per-building ROI breakdown")
    private List<BuildingRoiResponse> buildings;
}
