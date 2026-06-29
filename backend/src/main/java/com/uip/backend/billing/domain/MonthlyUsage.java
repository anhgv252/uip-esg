package com.uip.backend.billing.domain;

import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * M5-4 T01: Monthly usage aggregation table.
 * Daily job aggregates metering_events into this table for fast billing dashboard queries.
 * 
 * Billing model:
 * - Base fee: 2M VND/building/month
 * - AI tokens: 100K baseline, 50 VND per 1K tokens overage
 */
@Entity
@Table(name = "monthly_usage", schema = "billing",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "building_id", "billing_month"}))
@EntityListeners(com.uip.backend.tenant.hibernate.TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyUsage implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId = "default";

    @Column(name = "building_id", length = 100)
    private String buildingId;

    /** YYYY-MM format (e.g., 2026-06) */
    @Column(name = "billing_month", nullable = false, length = 7)
    private String billingMonth;

    @Column(name = "total_sensor_readings")
    private Long totalSensorReadings = 0L;

    @Column(name = "total_ai_inferences")
    private Long totalAiInferences = 0L;

    @Column(name = "total_ai_tokens")
    private Long totalAiTokens = 0L;

    @Column(name = "total_alerts")
    private Long totalAlerts = 0L;

    @Column(name = "total_workflow_executions")
    private Long totalWorkflowExecutions = 0L;

    /** Base subscription fee: 2M VND/building/month */
    @Column(name = "base_fee_vnd")
    private Long baseFeeVnd = 2_000_000L;

    /** AI token overage cost: 50 VND per 1K tokens above 100K baseline */
    @Column(name = "ai_overage_vnd")
    private Long aiOverageVnd = 0L;

    /** Total cost = base_fee_vnd + ai_overage_vnd */
    @Column(name = "total_cost_vnd")
    private Long totalCostVnd = 0L;

    @Column(name = "aggregated_at", nullable = false)
    private Instant aggregatedAt;

    @PrePersist
    protected void prePersist() {
        if (aggregatedAt == null) {
            aggregatedAt = Instant.now();
        }
    }
}
