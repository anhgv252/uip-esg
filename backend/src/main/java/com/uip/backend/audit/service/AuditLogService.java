package com.uip.backend.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.audit.domain.AuditEvent;
import com.uip.backend.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * M5-4 T09: Audit Log Service (SP:3)
 * 
 * Immutable append-only audit trail for critical business events:
 * - Billing: invoice generation
 * - LOTUS: certification calculation
 * - ESG: report generation
 * 
 * RLS policies enforce immutability (no UPDATE/DELETE).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Log an audit event (immutable, append-only).
     * 
     * @param tenantId Tenant identifier
     * @param eventType Event classification (e.g., BILLING_INVOICE_GENERATED)
     * @param actorId Who triggered the event (user UUID or "SYSTEM")
     * @param entityId Invoice ID, Building ID, Report ID, etc.
     * @param entityType INVOICE, BUILDING, REPORT, CERTIFICATION
     * @param metadata Event-specific JSON payload
     */
    @Transactional
    public void logEvent(String tenantId, String eventType, String actorId, 
                         String entityId, String entityType, Map<String, Object> metadata) {
        try {
            String metadataJson = objectMapper.writeValueAsString(metadata);
            
            AuditEvent event = AuditEvent.builder()
                    .tenantId(tenantId)
                    .eventType(eventType)
                    .actorId(actorId)
                    .entityId(entityId)
                    .entityType(entityType)
                    .metadata(metadataJson)
                    .build();
            
            auditEventRepository.save(event);
            
            log.info("Audit event logged: type={} entity={}/{} actor={}", 
                    eventType, entityType, entityId, actorId);
        } catch (Exception e) {
            log.error("Failed to log audit event: type={} entity={}/{}", 
                    eventType, entityType, entityId, e);
        }
    }

    /**
     * Convenience method for SYSTEM-triggered events.
     */
    @Transactional
    public void logSystemEvent(String tenantId, String eventType, String entityId, 
                                String entityType, Map<String, Object> metadata) {
        logEvent(tenantId, eventType, "SYSTEM", entityId, entityType, metadata);
    }
}
