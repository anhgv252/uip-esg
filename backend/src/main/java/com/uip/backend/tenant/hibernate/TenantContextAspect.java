package com.uip.backend.tenant.hibernate;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
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
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantContextAspect {

    private static final String SET_TENANT_SQL = "SET LOCAL app.tenant_id = '%s'";

    private final EntityManager entityManager;

    @Before("@annotation(transactional)")
    public void setTenantContext(Transactional transactional) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.getDefaultTenant();
        }

        // Sanitize: replace single quotes to prevent SQL injection
        String sanitizedTenantId = tenantId.replace("'", "''");

        String sql = String.format(SET_TENANT_SQL, sanitizedTenantId);

        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
                log.debug("SET LOCAL app.tenant_id = '{}'", sanitizedTenantId);
            }
        });
    }
}
