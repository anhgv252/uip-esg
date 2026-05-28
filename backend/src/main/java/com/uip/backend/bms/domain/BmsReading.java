package com.uip.backend.bms.domain;

import java.time.Instant;

/**
 * Single reading value polled from a BMS device.
 */
public record BmsReading(
        String readingType,
        double value,
        String unit,
        Instant timestamp
) {}
