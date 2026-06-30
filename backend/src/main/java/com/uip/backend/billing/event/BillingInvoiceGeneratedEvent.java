package com.uip.backend.billing.event;

import com.uip.backend.billing.domain.Invoice;

import java.util.Map;

/**
 * Published by {@code InvoiceGenerationService} when an invoice is generated.
 *
 * <p>Decouples billing from audit (ADR-052, migration C4): instead of injecting
 * {@code AuditLogService} directly, billing publishes this event and the audit module
 * subscribes via {@code @TransactionalEventListener} to log the audit record. This keeps
 * the {@code billing → audit.service} dependency out of the source tree.</p>
 */
public class BillingInvoiceGeneratedEvent {

    private final String tenantId;
    private final String invoiceNumber;
    private final String billingPeriod;
    private final String invoiceId;
    private final long totalVnd;
    private final int buildingCount;

    public BillingInvoiceGeneratedEvent(Invoice invoice, int buildingCount) {
        this.tenantId      = invoice.getTenantId();
        this.invoiceNumber = invoice.getInvoiceNumber();
        this.billingPeriod = invoice.getBillingPeriod();
        this.invoiceId     = invoice.getId() != null ? invoice.getId().toString() : "";
        this.totalVnd      = invoice.getTotalVnd();
        this.buildingCount = buildingCount;
    }

    public String getTenantId()      { return tenantId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getBillingPeriod() { return billingPeriod; }
    public String getInvoiceId()     { return invoiceId; }
    public long   getTotalVnd()      { return totalVnd; }
    public int    getBuildingCount() { return buildingCount; }

    /** Audit metadata payload derived from the event. */
    public Map<String, Object> toAuditMetadata() {
        return Map.of(
                "invoiceNumber", invoiceNumber,
                "billingPeriod", billingPeriod,
                "totalVnd",      totalVnd,
                "buildingCount", buildingCount
        );
    }
}
