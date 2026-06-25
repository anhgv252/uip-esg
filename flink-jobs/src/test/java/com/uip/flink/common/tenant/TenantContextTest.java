package com.uip.flink.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link TenantContext} — fail-closed set, get/require/clear.
 * Pure POJO (no Flink runtime).
 */
@DisplayName("TenantContext — fail-closed ThreadLocal holder (ADR-047)")
class TenantContextTest {

    @AfterEach
    void clearThread() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("set(null) / set(blank) throws — fail-closed")
    void set_rejectsNullAndBlank() {
        assertThrows(IllegalStateException.class, () -> TenantContext.set(null));
        assertThrows(IllegalStateException.class, () -> TenantContext.set(""));
        assertThrows(IllegalStateException.class, () -> TenantContext.set("   "));
        assertNull(TenantContext.get(), "Rejected set must not leak into the ThreadLocal");
    }

    @Test
    @DisplayName("set then get returns the bound tenant")
    void set_thenGet_returnsBound() {
        TenantContext.set("tenant-A");
        assertEquals("tenant-A", TenantContext.get());
        assertEquals("tenant-A", TenantContext.require());
    }

    @Test
    @DisplayName("require() throws when nothing is bound")
    void require_throwsWhenUnbound() {
        assertNull(TenantContext.get());
        assertThrows(IllegalStateException.class, TenantContext::require);
    }

    @Test
    @DisplayName("clear() unbinds and is safe to call repeatedly")
    void clear_unbindsSafely() {
        TenantContext.set("tenant-A");
        TenantContext.clear();
        assertNull(TenantContext.get());
        // idempotent — second clear must not throw
        TenantContext.clear();
        assertNull(TenantContext.get());
    }
}
