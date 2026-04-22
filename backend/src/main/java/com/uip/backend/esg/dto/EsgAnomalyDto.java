package com.uip.backend.esg.dto;

public record EsgAnomalyDto(
        String metricType,
        Double currentValue,
        Double historicalAvg,
        String buildingId,
        String period
) {}
