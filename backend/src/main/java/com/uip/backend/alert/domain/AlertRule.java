package com.uip.backend.alert.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_rules", schema = "alerts")
@Getter
@Setter
@NoArgsConstructor
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(nullable = false, length = 30)
    private String module;

    @Column(name = "measure_type", nullable = false, length = 50)
    private String measureType;

    @Column(nullable = false, length = 10)
    private String operator;

    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes = 10;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
