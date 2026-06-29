package com.uip.backend.billing.domain;

import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * M5-4 T02: Monthly invoice generated from billing.monthly_usage aggregates.
 * 
 * Invoice lifecycle: GENERATED → SENT → PAID (or DISPUTED)
 * Line items stored as JSONB: [{buildingId, baseFee, aiOverage, total, sensors, aiTokens}]
 */
@Entity
@Table(name = "invoices", schema = "billing")
@EntityListeners(com.uip.backend.tenant.hibernate.TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId = "default";

    /** Unique invoice identifier: INV-YYYY-MM-{tenantId}-{seq} */
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    /** YYYY-MM format (e.g., 2026-06) */
    @Column(name = "billing_period", nullable = false, length = 7)
    private String billingPeriod;

    @Column(name = "subtotal_vnd", nullable = false)
    private Long subtotalVnd;

    /** 10% VAT for Vietnam */
    @Column(name = "tax_vnd")
    private Long taxVnd = 0L;

    @Column(name = "total_vnd", nullable = false)
    private Long totalVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.GENERATED;

    /** JSON array: [{buildingId, baseFee, aiOverage, totalCost, sensors, aiTokens}] */
    @Column(name = "line_items", columnDefinition = "jsonb")
    private String lineItems;

    /** Path to PDF in MinIO/S3 (stub: returns HTML for now) */
    @Column(name = "pdf_path", length = 500)
    private String pdfPath;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void prePersist() {
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
        // Default due date: 30 days after generation
        if (dueDate == null) {
            dueDate = LocalDate.now().plusDays(30);
        }
    }
}
