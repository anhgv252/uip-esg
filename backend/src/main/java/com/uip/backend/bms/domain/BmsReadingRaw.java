package com.uip.backend.bms.domain;

import com.uip.backend.tenant.domain.TenantAware;
import com.uip.backend.tenant.hibernate.TenantEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bms_readings_raw", schema = "bms")
@EntityListeners(TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class BmsReadingRaw implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private BmsDevice device;

    @Column(name = "reading_type", nullable = false, length = 100)
    private String readingType;

    @Column(nullable = false)
    private Double value;

    @Column(length = 20)
    private String unit;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "ingested_at")
    private Instant ingestedAt;

    @PrePersist
    void prePersist() {
        ingestedAt = Instant.now();
    }
}
