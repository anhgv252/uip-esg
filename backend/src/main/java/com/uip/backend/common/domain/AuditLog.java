package com.uip.backend.common.domain;

import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log", schema = "public")
@EntityListeners(com.uip.backend.tenant.hibernate.TenantEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuditLog implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String resourceType;

    private String resourceId;

    @Builder.Default
    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(nullable = false)
    private Instant timestamp;

    @Column(columnDefinition = "jsonb")
    private String details;
}
