package com.uip.backend.aiworkflow.repository;

import com.uip.backend.aiworkflow.model.WorkflowDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for WorkflowDefinition entities.
 * Tenant isolation via RLS + explicit tenantId filter in queries.
 */
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    /**
     * List active workflow definitions for a tenant (paginated).
     */
    Page<WorkflowDefinition> findByTenantIdAndIsActiveTrue(String tenantId, Pageable pageable);

    /**
     * Find an active definition by ID and tenant.
     */
    Optional<WorkflowDefinition> findByIdAndTenantIdAndIsActiveTrue(UUID id, String tenantId);

    /**
     * Check if a workflow with the given name exists for the tenant.
     */
    boolean existsByTenantIdAndNameAndIsActiveTrue(String tenantId, String name);
}
