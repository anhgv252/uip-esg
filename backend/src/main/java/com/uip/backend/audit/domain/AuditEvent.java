package com.uip.backend.audit.domain;

import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * M5-4 T09: Immutable audit event (append-only, no updates/deletes).
 * RLS policies enforce immutability at DB level.
 * 
 * Critical events: billing, LOTUS certification, ESG report generation.
 */
@Entity
@Table(name = "events", schema = "audit")
@EntityListeners(com.uip.backend.tenant.hibernate.TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId = "default";

    /** Event classification: BILLING_INVOICE_GENERATED, LOTUS_CERT_CALCULATED, ESG_REPORT_GENERATED, etc. */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** Who triggered the event: user UUID or 'SYSTEM' for automated processes */
    @Column(name = "actor_id", length = 100)
    private String actorId;

    /** Invoice ID, Building ID, Report ID, etc. */
    @Column(name = "entity_id", length = 200)
    private String entityId;

    /** INVOICE, BUILDING, REPORT, CERTIFICATION */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** Event-specific JSON payload (e.g., {invoiceAmount: 10000000, buildings: 5}) */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @PrePersist
    protected void prePersist() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (actorId == null) {
            actorId = "SYSTEM";  // Default to SYSTEM for automated events
        }
    }
}
