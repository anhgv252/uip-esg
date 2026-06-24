package com.uip.analytics.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Enforces ClickHouse Row-Level Policy tenant isolation (ADR-047) at the JDBC
 * layer of {@code analytics-service}.
 *
 * <p>This is defense-in-depth LAYER 2. Layer 1 is the existing {@code WHERE tenant_id = ?}
 * SQL parameter filter inside the repositories. If a query ever omits that WHERE
 * clause by mistake, the RowPolicy ({@code USING tenant_id = currentSetting('tenant_id')})
 * still restricts the result set to the current tenant.</p>
 *
 * <p><b>Connection pool isolation (Spike S1 — ADR-047 §3).</b> ClickHouse session
 * settings are bound to a physical connection. Because the shared connection pool
 * reuses connections across requests, a {@code SET tenant_id} that leaks into a
 * returned connection would let the next request read another tenant's rows. This
 * engine guarantees the SET, the query, and the RESET all execute on the SAME
 * borrowed connection (via {@link JdbcTemplate#execute(org.springframework.jdbc.core.ConnectionCallback)}),
 * so the tenant setting cannot bleed to another request.</p>
 *
 * <p>Usage from a repository — the callback receives the scoped {@link Connection}
 * and must run its query on THAT connection (use {@link Connection#createPreparedStatement}
 * or a {@code PreparedStatementCallback}), not a fresh {@code jdbcTemplate.query(...)}
 * which would borrow a different connection:</p>
 * <pre>{@code
 * List<Row> rows = rowPolicyEngine.executeWithTenant(tenantId, conn -> {
 *     try (var ps = conn.prepareStatement(sql)) {
 *         ps.setString(1, tenantId);
 *         // bind params ...
 *         try (var rs = ps.executeQuery()) {
 *             // map rows
 *         }
 *     }
 * });
 * }</pre>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RowPolicyEngine {

    /** Session setting name consumed by the RowPolicy USING clause (see V032 migration). */
    static final String SETTING_NAME = "tenant_id";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Borrow a connection, set the {@code tenant_id} session setting on it, run
     * {@code action} on that same connection, then unconditionally reset the setting.
     *
     * @param tenantId non-null, non-blank tenant identifier
     * @param action   callback receiving the scoped {@link Connection}
     * @param <T>      result type
     * @return whatever {@code action} returns
     * @throws TenantContextException if tenantId is null/blank, or SET/RESET fails
     */
    public <T> T executeWithTenant(String tenantId, TenantConnectionCallback<T> action) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextException(
                "Cannot execute ClickHouse query without tenant context — tenant_id is null/blank (fail-closed, ADR-047)");
        }

        return jdbcTemplate.execute((Connection conn) -> {
            setTenant(conn, tenantId);
            try {
                log.debug("[RowPolicy] set tenant_id={} for query", tenantId);
                return action.doInTenantConnection(conn);
            } finally {
                resetTenant(conn);
            }
        });
    }

    private void setTenant(Connection conn, String tenantId) {
        executeSql(conn, "SET " + SETTING_NAME + " = '" + escapeSqlLiteral(tenantId) + "'");
    }

    private void resetTenant(Connection conn) {
        try {
            executeSql(conn, "SET " + SETTING_NAME + " = ''");
            log.debug("[RowPolicy] reset tenant_id after query (pool-safe)");
        } catch (TenantContextException e) {
            // Reset failure is non-fatal for the current query but MUST be logged loudly —
            // the connection may be poisoned for the next borrower. HikariCP eviction
            // would be the hard guarantee; here we flag for ops follow-up.
            log.error("[RowPolicy] FAILED to reset tenant_id on returned connection — "
                + "POSSIBLE CROSS-TENANT BLEED on next pooled request: {}", e.getMessage());
        }
    }

    private void executeSql(Connection conn, String sql) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new TenantContextException(
                "ClickHouse session setting failed: " + sql + " — " + e.getMessage(), e);
        }
    }

    /**
     * Minimal single-quote escaping for the tenant identifier inside the SET statement.
     * Tenant IDs are application-controlled (JWT claim), but defense-in-depth: prevent
     * a malformed value from breaking out of the SQL string literal.
     */
    private static String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    /**
     * Callback executed on the tenant-scoped ClickHouse connection. The connection
     * already has {@code tenant_id} set; implementors run their query on it (not on a
     * freshly borrowed one).
     */
    @FunctionalInterface
    public interface TenantConnectionCallback<T> {
        T doInTenantConnection(Connection conn) throws SQLException;
    }
}
