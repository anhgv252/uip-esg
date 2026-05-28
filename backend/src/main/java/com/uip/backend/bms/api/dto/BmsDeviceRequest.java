package com.uip.backend.bms.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record BmsDeviceRequest(
        @NotBlank String deviceName,
        @NotNull String protocol,
        String host,
        Integer port,
        Integer unitId,
        Integer deviceId,
        Integer pollInterval,
        Map<String, Object> metadata
) {}
