package com.uip.backend.citizen.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumptionHistoryDto {
    private UUID id;
    private LocalDateTime recordedAt;
    private BigDecimal readingValue;
    private BigDecimal unitsUsed;
}
