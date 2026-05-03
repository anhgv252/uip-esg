package com.uip.backend.tenant.domain;

/**
 * Marker interface for multi-tenant entities.
 * All domain entities should implement this.
 * TenantEntityListener auto-fills tenantId on @PrePersist.
 */
public interface TenantAware {

    String getTenantId();

    void setTenantId(String tenantId);
}
