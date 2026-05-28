package com.uip.backend.bms.domain;

import com.uip.backend.tenant.domain.TenantAware;
import com.uip.backend.tenant.hibernate.TenantEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "bms_devices", schema = "bms", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "device_name"})
})
@EntityListeners(TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class BmsDevice implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BmsProtocol protocol;

    private String host;

    private Integer port;

    @Column(name = "unit_id")
    private Integer unitId;

    @Column(name = "device_id")
    private Integer deviceId;

    @Column(name = "poll_interval")
    private Integer pollInterval = 5000;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(length = 20)
    private String status = "UNKNOWN";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
