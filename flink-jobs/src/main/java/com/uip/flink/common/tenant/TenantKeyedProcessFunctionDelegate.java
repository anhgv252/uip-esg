package com.uip.flink.common.tenant;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Executes a single Flink {@code processElement} call inside a tenant-scoped
 * ThreadLocal guard (ADR-047 §1.3 — Flink tenant isolation pattern).
 *
 * <p>This is the runtime half of the tenant-isolation contract; the concrete
 * Flink operator delegates here so the tenant-extract → bind → process → clear
 * lifecycle is centralized and cannot be forgotten. The {@code Collector} is
 * passed as a {@link BiConsumer} to keep this class free of Flink API types.</p>
 *
 * <p>Fail-closed: if {@code extractor} returns null/blank, the record is dropped
 * (not processed) and a {@link #droppedNoTenant()} counter is incremented. This
 * prevents an operator from emitting output under an unbound tenant context.</p>
 *
 * <p>Thread-safety: {@code droppedNoTenant} is a plain {@code long} — the delegate
 * is intended to be held as a non-shared field of a single Flink operator
 * instance, and {@code processElement} is invoked by one task-manager thread at
 * a time per operator subtask. Do not share one delegate across operators.</p>
 */
public final class TenantKeyedProcessFunctionDelegate<IN, OUT> {

    private final TenantKeyedProcessFunction.TenantExtractor<IN> extractor;
    private long droppedNoTenant = 0L;

    private TenantKeyedProcessFunctionDelegate(
            TenantKeyedProcessFunction.TenantExtractor<IN> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("TenantExtractor must not be null (ADR-047)");
        }
        this.extractor = extractor;
    }

    public static <IN, OUT> TenantKeyedProcessFunctionDelegate<IN, OUT> forFn(
            TenantKeyedProcessFunction.TenantExtractor<IN> extractor) {
        return new TenantKeyedProcessFunctionDelegate<>(extractor);
    }

    /**
     * Run one record through the tenant guard. {@code processor} receives the
     * record and an emitter; it executes with {@link TenantContext} bound to the
     * record's tenant.
     *
     * @param record    the Flink input element
     * @param processor tenant-scoped work; receives record + emitter
     * @param emitter   forwards each OUT to the Flink Collector
     */
    public void run(IN record, BiConsumer<IN, Consumer<OUT>> processor, Consumer<OUT> emitter) {
        String tenantId = null;
        try {
            tenantId = extractor.extract(record);
        } catch (RuntimeException e) {
            // extractor threw — treat as no-tenant, fail-closed drop.
            tenantId = null;
        }
        if (tenantId == null || tenantId.isBlank()) {
            droppedNoTenant++;
            return; // fail-closed: do NOT process under unbound tenant
        }
        TenantContext.set(tenantId);
        try {
            processor.accept(record, emitter);
        } finally {
            TenantContext.clear();
        }
    }

    /** Count of records dropped because no tenant could be extracted (monitor this). */
    public long droppedNoTenant() {
        return droppedNoTenant;
    }
}
