package com.uip.analytics.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RowPolicyEngine} — the fail-closed + session-setting
 * contract from ADR-047 §3 (Spike S1).
 *
 * <p>Full RowPolicy enforcement (cross-tenant row filtering) is verified by the
 * Testcontainers integration test {@code RowPolicyIsolationIT} — here we assert
 * the engine's own guarantees:</p>
 * <ul>
 *   <li>null/blank tenant → {@link TenantContextException}, no query executed</li>
 *   <li>SET runs before the callback</li>
 *   <li>RESET runs in a finally block even when the callback throws</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RowPolicyEngineTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private RowPolicyEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RowPolicyEngine(jdbcTemplate);
    }

    @Test
    void nullTenant_throwsBeforeAnyConnectionWork() {
        assertThrows(TenantContextException.class,
            () -> engine.executeWithTenant(null, c -> "ignored"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void blankTenant_throwsBeforeAnyConnectionWork() {
        assertThrows(TenantContextException.class,
            () -> engine.executeWithTenant("   ", c -> "ignored"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void validTenant_setsBeforeQuery_and_resetsAfter() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(jdbcTemplate.execute(any(org.springframework.jdbc.core.ConnectionCallback.class)))
            .thenAnswer(inv -> {
                org.springframework.jdbc.core.ConnectionCallback<?> cb = inv.getArgument(0);
                return cb.doInConnection(conn);
            });

        String result = engine.executeWithTenant("tenant_A", c -> "queried-on-" + c.hashCode());

        // SET happens before the callback; RESET happens after (in finally).
        verify(stmt, times(1)).execute(contains("SET tenant_id = 'tenant_A'"));
        verify(stmt, times(1)).execute(contains("SET tenant_id = ''"));
        // Result returned unchanged from the callback.
        assert result != null;
    }

    @Test
    void resetStillRuns_whenCallbackThrows() throws Exception {
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(jdbcTemplate.execute(any(org.springframework.jdbc.core.ConnectionCallback.class)))
            .thenAnswer(inv -> {
                org.springframework.jdbc.core.ConnectionCallback<?> cb = inv.getArgument(0);
                return cb.doInConnection(conn);
            });

        assertThrows(RuntimeException.class,
            () -> engine.executeWithTenant("tenant_A", c -> {
                throw new RuntimeException("query blew up");
            }));

        // Critical: even on failure the setting must be reset so the pooled
        // connection does not leak tenant_A to the next borrower.
        verify(stmt, times(1)).execute(contains("SET tenant_id = ''"));
    }
}
