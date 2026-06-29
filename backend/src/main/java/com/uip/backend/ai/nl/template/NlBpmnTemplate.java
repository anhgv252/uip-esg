package com.uip.backend.ai.nl.template;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * DB-backed BPMN template for NL→BPMN intent grounding.
 *
 * <p>Allows operators/admins to customise workflow templates at runtime
 * via the admin API — no redeployment required.
 */
@Entity
@Table(name = "nl_bpmn_templates", schema = "ai")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NlBpmnTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Workflow intent key — must match one of the 10 MVP4 intents. */
    @Column(nullable = false, unique = true, length = 100)
    private String intent;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column
    private String description;

    /** Full BPMN 2.0 XML with {{placeholder}} syntax. */
    @Column(name = "bpmn_xml", nullable = false, columnDefinition = "TEXT")
    private String bpmnXml;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by", nullable = false, length = 100)
    @Builder.Default
    private String createdBy = "system";

    @Column(name = "updated_by", nullable = false, length = 100)
    @Builder.Default
    private String updatedBy = "system";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
