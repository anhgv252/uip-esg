package com.uip.backend.ai.nl.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * AI-generated BPMN workflow draft entity.
 *
 * <p>M5-3 T01: Stores synthesised workflows pending operator review.
 * Each draft represents one NL→BPMN generation with operator approval gate.
 */
@Entity
@Table(name = "workflow_drafts", schema = "ai")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(nullable = false, length = 100)
    private String intent;

    @Column(name = "bpmn_xml", nullable = false, columnDefinition = "TEXT")
    private String bpmnXml;

    @Column
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private DraftStatus status = DraftStatus.PENDING_REVIEW;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "extracted_entities", columnDefinition = "jsonb")
    private String extractedEntities;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "nl_parse_latency_ms")
    private Long nlParseLatencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
