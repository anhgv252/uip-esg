package com.uip.backend.esg.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * ADR-014 — Error message from telemetry validation failures.
 * Consumed from topic: UIP.esg.telemetry.error.v1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryErrorDto(
        String errorCode,
        String sensorId,
        String message,
        Instant detectedAt
) {}
