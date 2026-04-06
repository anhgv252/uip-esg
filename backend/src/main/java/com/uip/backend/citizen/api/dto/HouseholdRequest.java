package com.uip.backend.citizen.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdRequest {
    @NotNull(message = "Building ID is required")
    private UUID buildingId;

    private String floor;
    private String unitNumber;
}
