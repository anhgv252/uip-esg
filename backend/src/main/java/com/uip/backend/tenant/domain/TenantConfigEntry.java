package com.uip.backend.tenant.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "tenant_config", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(TenantConfigEntryId.class)
public class TenantConfigEntry {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Id
    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
