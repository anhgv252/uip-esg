package com.uip.backend.citizen.api.dto;

public record ServiceRequestResponse(
        String requestId,
        String processInstanceId,
        String status
) {}
