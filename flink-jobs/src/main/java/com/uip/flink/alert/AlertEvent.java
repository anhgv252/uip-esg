package com.uip.flink.alert;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent implements Serializable {
    private String sensorId;
    private String module;
    private String measureType;
    private double value;
    private double threshold;
    private String severity;   // WARNING, CRITICAL, EMERGENCY
    private Instant detectedAt;
}
