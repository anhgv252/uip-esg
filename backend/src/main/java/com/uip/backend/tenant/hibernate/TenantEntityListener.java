package com.uip.backend.tenant.hibernate;

import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.PrePersist;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA entity listener that auto-fills tenant_id on @PrePersist.
 * Registered on entities via @EntityListeners(TenantEntityListener.class).
 *
 * Not a @Component -- JPA instantiates entity listeners directly.
 * Reads tenant_id from TenantContext ThreadLocal set by TenantContextFilter.
 *
 * ADR-021: Falls back to "default" when TenantContext has no tenant set.
 */
@Slf4j
public class TenantEntityListener {

    @PrePersist
    public void prePersist(Object entity) {
        if (entity instanceof TenantAware tenantAware) {
            if (tenantAware.getTenantId() == null || tenantAware.getTenantId().isBlank()) {
                String tenantId = TenantContext.getCurrentTenant();
                if (tenantId == null || tenantId.isBlank()) {
                    tenantId = TenantContext.getDefaultTenant();
                }
                tenantAware.setTenantId(tenantId);
                log.debug("Auto-set tenantId={} on entity {}", tenantId, entity.getClass().getSimpleName());
            }
        }
    }
}
