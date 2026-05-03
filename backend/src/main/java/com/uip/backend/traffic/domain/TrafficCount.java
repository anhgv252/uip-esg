package com.uip.backend.traffic.domain;

import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "traffic_counts", schema = "traffic")
@EntityListeners(com.uip.backend.tenant.hibernate.TenantEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrafficCount implements TenantAware {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Builder.Default
    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "intersection_id", nullable = false)
    private String intersectionId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "vehicle_count", nullable = false)
    private Integer vehicleCount;

    @Column(name = "vehicle_type")
    private String vehicleType; // CAR, MOTORCYCLE, TRUCK, BUS

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
