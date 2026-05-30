package com.uip.backend.aiworkflow.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight summary DTO for workflow list endpoint — excludes bpmnXml and camundaDeploymentId
 * to prevent megabyte transfers and avoid exposing internal Camunda IDs.
 */
public record WorkflowSummaryDto(
        UUID id,
        String tenantId,
        String name,
        String description,
        Integer version,
        Boolean isActive,
        boolean deployed,
        Instant createdAt,
        Instant updatedAt
) {}
