package com.uip.backend.esg.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsgMetricDto {
    private String  sourceId;
    private String  metricType;
    private Instant timestamp;
    private Double  value;
    private String  unit;
    private String  buildingId;
    private String  districtCode;
}
