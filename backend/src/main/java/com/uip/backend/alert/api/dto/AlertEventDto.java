package com.uip.backend.alert.api.dto;

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
public class AlertEventDto {
    private UUID    id;
    private UUID    ruleId;
    private String  sensorId;
    private String  module;
    private String  measureType;
    private Double  value;
    private Double  threshold;
    private String  severity;
    private String  status;
    private Instant detectedAt;
    private UUID    acknowledgedBy;
    private Instant acknowledgedAt;
    private String  note;
}
