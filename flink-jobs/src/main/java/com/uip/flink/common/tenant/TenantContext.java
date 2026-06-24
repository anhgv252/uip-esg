package com.uip.flink.common.tenant;

/**
 * Thread-local holder for the active tenant identifier inside a Flink operator
 * (ADR-047 §1.3 — Flink tenant isolation pattern).
 *
 * <p>Flink operators process records on task-manager threads. To make the
 * current tenant visible to downstream code (sinks, side outputs, metrics)
 * without threading it through every method signature, the tenant delegate
 * sets this ThreadLocal in {@code processElement} and clears it in a finally
 * block.</p>
 *
 * <p><b>Never leak.</b> The contract is: setter and clearer are always paired on the
 * same thread within a single {@code processElement} call. Failure to clear leaks
 * the tenant into the next record processed on the same thread (cross-tenant bleed).</p>
 *
 * <p>This class is Flink-API-free so it can be unit-tested without the Flink runtime.
 * It is the in-tree source of truth for the contract also mirrored (as a forward
 * guard) in {@code com.uip.backend.tenant.flink} — see ADR-047 §1.4.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    /** Bind {@code tenantId} to the current thread. Null/blank is rejected (fail-closed). */
    public static void set(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException(
                "Cannot set TenantContext — tenantId is null/blank (fail-closed, ADR-047)");
        }
        CURRENT.set(tenantId);
    }

    /** Return the bound tenantId, or {@code null} if none set on this thread. */
    public static String get() {
        return CURRENT.get();
    }

    /** Return the bound tenantId, throwing if none (use when a tenant is mandatory). */
    public static String require() {
        String t = CURRENT.get();
        if (t == null) {
            throw new IllegalStateException(
                "No TenantContext bound to current thread — required for this operation (ADR-047)");
        }
        return t;
    }

    /** Unbind the tenantId. Safe to call when nothing is set. */
    public static void clear() {
        CURRENT.remove();
    }
}
