package com.uip.backend.common.service;

import com.uip.backend.common.domain.AuditLog;
import com.uip.backend.common.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logAction(String actor, String action, String resourceType, String resourceId) {
        logAction(actor, action, resourceType, resourceId, null, null);
    }

    @Transactional
    public void logAction(String actor, String action, String resourceType, String resourceId, String tenantId) {
        logAction(actor, action, resourceType, resourceId, tenantId, null);
    }

    @Transactional
    public void logAction(String actor, String action, String resourceType,
                          String resourceId, String tenantId, Map<String, Object> details) {
        AuditLog auditLog = AuditLog.builder()
                .actor(actor)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .tenantId(tenantId)
                .timestamp(Instant.now())
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit log: actor={} action={} resource={}/{} tenant={}",
                actor, action, resourceType, resourceId, tenantId);
    }
}
