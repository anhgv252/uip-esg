package com.uip.backend.environment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorDto {
    private UUID   id;
    private String sensorId;
    private String sensorName;
    private String sensorType;
    private String districtCode;
    private Double latitude;
    private Double longitude;
    private String status;       // ONLINE / OFFLINE
    private boolean active;
    private Instant lastSeenAt;
    private Instant installedAt;
}
