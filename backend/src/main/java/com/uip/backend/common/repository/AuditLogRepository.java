package com.uip.backend.common.repository;

import com.uip.backend.common.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByResourceTypeAndResourceIdOrderByTimestampDesc(String resourceType, String resourceId);

    List<AuditLog> findByTenantIdOrderByTimestampDesc(String tenantId);

    List<AuditLog> findByActorOrderByTimestampDesc(String actor);
}
