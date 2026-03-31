package com.uip.backend.esg.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsgSummaryDto {
    private String  period;
    private Integer year;
    private Integer quarter;
    private Double  totalEnergyKwh;
    private Double  totalWaterM3;
    private Double  totalCarbonTco2e;
    private Double  totalWasteTons;
    private Long    sampleCount;
}
