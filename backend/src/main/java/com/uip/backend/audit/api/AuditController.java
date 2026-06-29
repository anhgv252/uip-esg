package com.uip.backend.audit.api;

import com.uip.backend.audit.domain.AuditEvent;
import com.uip.backend.audit.repository.AuditEventRepository;
import com.uip.backend.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * M5-4 T09: Audit Log REST API
 * 
 * Endpoint:
 * - GET /api/v1/audit/events (paginated, optional eventType filter)
 * 
 * Access: ADMIN, TENANT_ADMIN only
 */
@RestController
@RequestMapping("/api/v1/audit/events")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    /**
     * List audit events (paginated, optional eventType filter).
     * 
     * GET /api/v1/audit/events?page=0&size=50&eventType=BILLING_INVOICE_GENERATED
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<Page<AuditEvent>> listAuditEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String eventType) {
        String tenantId = TenantContext.getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size);
        
        Page<AuditEvent> events = (eventType != null && !eventType.isBlank())
                ? auditEventRepository.findByTenantIdAndEventTypeOrderByOccurredAtDesc(tenantId, eventType, pageable)
                : auditEventRepository.findByTenantIdOrderByOccurredAtDesc(tenantId, pageable);
        
        log.debug("Listing audit events for tenant {}: {} events found", tenantId, events.getTotalElements());
        
        return ResponseEntity.ok(events);
    }
}
