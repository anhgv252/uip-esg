package com.uip.backend.tenant.context;

/**
 * ThreadLocal holder for current tenant_id.
 * Set by TenantContextFilter (HTTP) or TenantAwareKafkaListener (Kafka).
 * Read by TenantContextAspect to run SET LOCAL before queries.
 *
 * ADR-010: RLS isolation via SET LOCAL app.tenant_id
 * ADR-021: T1 fallback to 'default'
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final String DEFAULT_TENANT = "default";

    private TenantContext() {}

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            CURRENT_TENANT.set(DEFAULT_TENANT);
        } else {
            CURRENT_TENANT.set(tenantId);
        }
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static String getDefaultTenant() {
        return DEFAULT_TENANT;
    }
}
