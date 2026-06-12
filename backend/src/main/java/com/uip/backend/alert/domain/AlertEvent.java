package com.uip.backend.alert.domain;

import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// acknowledged_by lưu username (String) thay vì UUID để alert-module không phụ thuộc auth-module.
// Xem: docs/architecture/modular-architecture-evaluation.md — Module Boundary Rules

@Entity
@Table(name = "alert_events", schema = "alerts")
@EntityListeners(com.uip.backend.tenant.hibernate.TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AlertEvent implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "sensor_id", nullable = false, length = 100)
    private String sensorId;

    @Column(nullable = false, length = 30)
    private String module;

    @Column(name = "measure_type", nullable = false, length = 50)
    private String measureType;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** Username của operator đã acknowledge — lưu String để tránh coupling với auth-module. */
    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    /** Location description for map overlay (e.g. district name, coordinates) */
    @Column(name = "location", length = 200)
    private String location;

    /** Building associated with this alert — set by structural/BMS consumers for safety score correlation. */
    @Column(name = "building_id", length = 100)
    private String buildingId;

    // ─── Operator feedback (M4-COR-06) ───────────────────────────────────────

    /** {@code true} = operator confirmed AI was correct; {@code false} = AI was wrong. */
    @Column(name = "feedback_correct")
    private Boolean feedbackCorrect;

    /** Free-text comment from the operator. */
    @Column(name = "feedback_comment", columnDefinition = "TEXT")
    private String feedbackComment;

    /** Username of the operator who submitted feedback. */
    @Column(name = "feedback_by", length = 100)
    private String feedbackBy;

    /** Timestamp when feedback was submitted. */
    @Column(name = "feedback_at")
    private java.time.Instant feedbackAt;
}
