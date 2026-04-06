package com.uip.backend.citizen.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildingDto {
    private UUID id;
    private String name;
    private String address;
    private String district;
}
