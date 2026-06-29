package com.uip.backend.billing.domain;

/**
 * M5-4 T02: Invoice status lifecycle.
 * GENERATED → SENT → PAID (or DISPUTED)
 */
public enum InvoiceStatus {
    GENERATED,   // Invoice created, not yet sent to customer
    SENT,        // Invoice sent to customer (email/portal)
    PAID,        // Invoice paid by customer
    DISPUTED     // Customer disputed the invoice
}
