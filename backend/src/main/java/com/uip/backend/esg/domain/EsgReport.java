package com.uip.backend.esg.domain;

import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports", schema = "esg")
@EntityListeners(com.uip.backend.tenant.hibernate.TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class EsgReport implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType;  // QUARTERLY, ANNUAL

    @Column(nullable = false)
    private Integer year;

    private Integer quarter;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";  // PENDING, GENERATING, DONE, FAILED

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
