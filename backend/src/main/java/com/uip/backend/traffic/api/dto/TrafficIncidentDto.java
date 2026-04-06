package com.uip.backend.traffic.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrafficIncidentDto {
    private UUID id;
    private String intersectionId;
    private String incidentType; // ACCIDENT, CONGESTION, ROADWORK
    private String description;
    private Double latitude;
    private Double longitude;
    private String status; // OPEN, RESOLVED, ESCALATED
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
