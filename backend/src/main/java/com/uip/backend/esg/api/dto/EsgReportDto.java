package com.uip.backend.esg.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsgReportDto {
    private UUID    id;
    private String  periodType;
    private Integer year;
    private Integer quarter;
    private String  status;
    private String  downloadUrl;
    private Instant generatedAt;
    private Instant createdAt;
}
