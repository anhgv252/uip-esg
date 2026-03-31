package com.uip.backend.environment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AqiResponseDto {
    private String  sensorId;
    private Instant timestamp;
    private Integer aqiValue;
    private String  category;
    private String  color;
    private Double  pm25;
    private Double  pm10;
    private Double  o3;
    private Double  no2;
    private Double  so2;
    private Double  co;
    private String  districtCode;
}
