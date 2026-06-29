package com.uip.backend.ai.nl.repository;

import com.uip.backend.ai.nl.domain.DraftStatus;
import com.uip.backend.ai.nl.domain.WorkflowDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AI-generated workflow drafts.
 *
 * <p>M5-3 T01: Operator review workflow persistence.
 */
@Repository
public interface WorkflowDraftRepository extends JpaRepository<WorkflowDraft, UUID> {

    List<WorkflowDraft> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<WorkflowDraft> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, DraftStatus status);

    Optional<WorkflowDraft> findByIdAndTenantId(UUID id, String tenantId);
}
