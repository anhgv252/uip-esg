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
public class SensorReadingDto {
    private String  sensorId;
    private Instant timestamp;
    private Double  aqi;
    private Double  pm25;
    private Double  pm10;
    private Double  o3;
    private Double  no2;
    private Double  so2;
    private Double  co;
    private Double  temperature;
    private Double  humidity;
}
