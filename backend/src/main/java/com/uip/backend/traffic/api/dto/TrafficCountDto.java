package com.uip.backend.traffic.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficCountDto {
    private UUID id;
    private String intersectionId;
    private LocalDateTime recordedAt;
    private Integer vehicleCount;
    private String vehicleType;
}
