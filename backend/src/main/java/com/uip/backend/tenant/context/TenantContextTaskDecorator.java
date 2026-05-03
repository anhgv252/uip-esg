package com.uip.backend.tenant.context;

import org.springframework.core.task.TaskDecorator;

/**
 * Propagates TenantContext from parent thread to @Async child thread.
 * ADR-020: Non-HTTP Tenant ID Propagation
 */
public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String tenantId = TenantContext.getCurrentTenant();
        return () -> {
            try {
                TenantContext.setCurrentTenant(tenantId);
                runnable.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}
