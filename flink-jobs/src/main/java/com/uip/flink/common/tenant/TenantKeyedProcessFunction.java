package com.uip.flink.common.tenant;

/**
 * Mandatory base contract for every tenant-aware keyed Flink operator
 * (ADR-047 §1.3 — Flink tenant isolation pattern).
 *
 * <p>This is the contract holder that every concrete operator (in the
 * {@code flink-jobs} module) keys off. Concrete operators do NOT extend this
 * class — it carries no Flink API. Instead they hold a
 * {@link TenantKeyedProcessFunctionDelegate} field and delegate
 * {@code processElement} to it, so the tenant-extract → bind ThreadLocal →
 * process → clear lifecycle is centralized and cannot be forgotten.</p>
 *
 * <p><b>Concrete operator pattern:</b></p>
 * <pre>{@code
 * public final class WelfordKeyedProcessFunction
 *         extends KeyedProcessFunction<String, NgsiLdMessage, NgsiLdMessage> {
 *     private final TenantKeyedProcessFunctionDelegate<NgsiLdMessage, NgsiLdMessage> guard =
 *         TenantKeyedProcessFunctionDelegate.forFn(NgsiLdMessage::getTenantId);
 *
 *     @Override
 *     public void processElement(NgsiLdMessage value, Context ctx, Collector<NgsiLdMessage> out) {
 *         guard.run(value, this::processInTenant, out::collect);
 *     }
 * }
 * }</pre>
 *
 * <p>The ArchUnit rule ({@code FlinkTenantArchTest}) asserts that every
 * {@code ProcessFunction} / {@code KeyedProcessFunction} / {@code PatternProcessFunction}
 * subclass in {@code com.uip.flink..} references
 * {@link TenantKeyedProcessFunctionDelegate} or {@link TenantContext}.</p>
 */
public final class TenantKeyedProcessFunction {

    private TenantKeyedProcessFunction() {}

    /**
     * Functional contract every tenant-aware operator satisfies: extract the tenant
     * id from an input record. Returning null/blank triggers fail-closed drop.
     *
     * @param <IN> input record type
     */
    @FunctionalInterface
    public interface TenantExtractor<IN> {
        String extract(IN record);
    }
}
