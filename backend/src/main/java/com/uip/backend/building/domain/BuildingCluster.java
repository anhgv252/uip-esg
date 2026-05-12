package com.uip.backend.building.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "buildings",
    schema = "public",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_building_tenant_code",
        columnNames = {"tenant_id", "building_code"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildingCluster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "building_code", nullable = false, length = 50)
    private String buildingCode;

    @Column(name = "building_name", nullable = false, length = 255)
    private String buildingName;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "cluster_id", length = 50)
    private String clusterId;

    @Column(name = "floor_count", nullable = false)
    @Builder.Default
    private Integer floorCount = 1;

    @Column(name = "total_area_m2")
    private Double totalAreaM2;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
