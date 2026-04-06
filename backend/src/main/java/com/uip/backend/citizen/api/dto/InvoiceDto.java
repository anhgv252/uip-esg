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
public class InvoiceDto {
    private UUID id;
    private Integer billingMonth;
    private Integer billingYear;
    private String meterType; // ELECTRICITY, WATER
    private BigDecimal unitsConsumed;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String status; // UNPAID, PAID, OVERDUE
    private LocalDateTime issuedAt;
    private LocalDateTime dueAt;
    private LocalDateTime paidAt;
}
