package com.uip.backend.traffic.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficCountDto {
    private String  intersectionId;
    private Instant timestamp;
    private Integer vehicleCount;
    private Double  avgSpeedKmh;
    private String  congestionLevel;
}
