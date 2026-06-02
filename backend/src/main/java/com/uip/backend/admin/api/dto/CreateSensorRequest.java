package com.uip.backend.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSensorRequest(
        @NotBlank @Size(max = 100)
        String sensorId,

        @NotBlank @Size(max = 200)
        String sensorName,

        @Size(max = 50)
        String sensorType,

        @Size(max = 20)
        String districtCode,

        Double latitude,
        Double longitude
) {}
