package com.uip.backend.audit.repository;

import com.uip.backend.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * M5-4 T09: Repository for immutable audit events.
 * Note: UPDATE and DELETE operations will fail due to RLS policies.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByTenantIdOrderByOccurredAtDesc(String tenantId, Pageable pageable);

    Page<AuditEvent> findByTenantIdAndEventTypeOrderByOccurredAtDesc(
            String tenantId, String eventType, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE a.tenantId = :tenantId AND a.occurredAt >= :startTime AND a.occurredAt <= :endTime ORDER BY a.occurredAt DESC")
    List<AuditEvent> findByTenantAndTimeRange(String tenantId, Instant startTime, Instant endTime);

    @Query("SELECT a FROM AuditEvent a WHERE a.entityType = :entityType AND a.entityId = :entityId ORDER BY a.occurredAt DESC")
    List<AuditEvent> findByEntity(String entityType, String entityId);
}
