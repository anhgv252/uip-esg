package com.uip.backend.billing.domain;

import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * M5-2 T07: Tenant metering event for AI cost tracking.
 * Consumed from Kafka topic: UIP.billing.metering.event.v1
 * 
 * Idempotency: event_id is unique (enforced by DB constraint + Redis dedup)
 * Multi-tenancy: tenant_id set by TenantEntityListener
 */
@Entity
@Table(name = "metering_events", schema = "billing")
@EntityListeners(com.uip.backend.tenant.hibernate.TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeteringEvent implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId = "default";

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private MeteringEventType eventType;

    @Column(name = "building_id", length = 50)
    private String buildingId;

    @Column(name = "sensor_id", length = 100)
    private String sensorId;

    @Column(name = "alert_id")
    private java.util.UUID alertId;

    @Column(name = "token_count")
    private Long tokenCount = 0L;

    @Column(name = "workflow_run_id")
    private java.util.UUID workflowRunId;

    /** Additional event context (model name, tokens used, etc.) stored as JSON */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /** Cost in USD cents (e.g., 150 = $1.50) */
    @Column(name = "cost_usd_cents", nullable = false)
    private Integer costUsdCents = 0;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @PrePersist
    protected void prePersist() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}
