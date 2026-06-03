package com.uip.backend.safety.dto;

import java.time.Instant;

/**
 * Single structural sensor reading data point for the 24h trend chart.
 * Populated once structural sensor readings pipeline (B2-5/B2-6) is active.
 */
public record VibrationReadingResponse(
        String sensorId,
        String sensorType,   // STRUCTURAL_VIBRATION | STRUCTURAL_TILT | STRUCTURAL_CRACK
        Instant timestamp,
        double value,
        String unit          // mm/s | mrad | mm
) {}
