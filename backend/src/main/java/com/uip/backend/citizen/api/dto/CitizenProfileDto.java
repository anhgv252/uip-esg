package com.uip.backend.citizen.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenProfileDto {
    private UUID id;
    private String username;
    private String email;
    private String phone;
    private String fullName;
    private String cccd;
    private String role;
    private LocalDateTime createdAt;
    private HouseholdDto household;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HouseholdDto {
        private UUID id;
        private UUID buildingId;
        private String buildingName;
        private String floor;
        private String unitNumber;
    }
}
