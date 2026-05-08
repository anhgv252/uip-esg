package com.uip.backend.tenant.hibernate;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AOP aspect that sets the PostgreSQL session variable app.tenant_id
 * before every @Transactional method execution.
 *
 * Uses SET LOCAL (ADR-010) so the variable is scoped to the current transaction
 * and automatically resets when the transaction completes.
 * SET SESSION would persist across transactions, which is a security risk in
 * a connection pool scenario.
 * BT-07e: Only loaded when multi-tenancy capability is enabled (T2+). T1 uses default tenant.
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "uip.capabilities", name = "multi-tenancy", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class TenantContextAspect {

    // Uses set_config() with a PreparedStatement parameter — eliminates SQL injection risk.
    // Third argument `true` scopes the setting to the current transaction (equivalent to SET LOCAL).
    private static final String SET_TENANT_SQL = "SELECT set_config('app.tenant_id', ?, true)";

    private final EntityManager entityManager;

    @Before("@annotation(transactional)")
    public void setTenantContext(Transactional transactional) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.getDefaultTenant();
        }

        final String resolvedTenantId = tenantId;
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var ps = connection.prepareStatement(SET_TENANT_SQL)) {
                ps.setString(1, resolvedTenantId);
                ps.execute();
                log.debug("SET LOCAL app.tenant_id = '{}'", resolvedTenantId);
            }
        });
    }
}
