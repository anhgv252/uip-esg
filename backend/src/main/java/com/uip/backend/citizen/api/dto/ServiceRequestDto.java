package com.uip.backend.citizen.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ServiceRequestDto(
        @NotBlank String requestType,
        @NotBlank String description,
        String district
) {}
