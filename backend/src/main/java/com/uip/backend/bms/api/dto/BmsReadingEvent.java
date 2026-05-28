package com.uip.backend.bms.api.dto;

import java.time.Instant;
import java.util.UUID;

public record BmsReadingEvent(
        UUID deviceId,
        String tenantId,
        String readingType,
        double value,
        String unit,
        Instant timestamp,
        String source
) {}
