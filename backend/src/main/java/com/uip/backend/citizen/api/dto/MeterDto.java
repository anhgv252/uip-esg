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
public class MeterDto {
    private UUID id;
    private String meterCode;
    private String meterType; // ELECTRICITY, WATER
    private LocalDateTime registeredAt;
}
