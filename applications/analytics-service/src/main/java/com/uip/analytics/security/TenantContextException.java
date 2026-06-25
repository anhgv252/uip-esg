package com.uip.analytics.security;

/**
 * Thrown when a ClickHouse query is attempted without a valid tenant context
 * (tenant_id is null or blank), or when the RowPolicy session setting cannot
 * be applied/reset on the borrowed connection.
 *
 * <p>Fail-closed by design (ADR-047 §2.3) — never execute a query that could
 * leak cross-tenant data. Caller must surface this as a 4xx/5xx, never swallow.</p>
 *
 * @see RowPolicyEngine
 */
public class TenantContextException extends RuntimeException {

    public TenantContextException(String message) {
        super(message);
    }

    public TenantContextException(String message, Throwable cause) {
        super(message, cause);
    }
}
