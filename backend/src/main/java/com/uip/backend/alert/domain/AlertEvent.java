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
}
