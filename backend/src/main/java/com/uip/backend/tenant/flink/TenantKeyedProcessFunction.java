package com.uip.backend.tenant.flink;

/**
 * Mandatory base class for every tenant-aware keyed Flink operator (ADR-047 §1.3).
 *
 * <p><b>Why this lives in the backend module.</b> The external Flink job submitter
 * ({@code Dockerfile.flink-submitter}) holds the Flink runtime and the actual
 * {@code KeyedProcessFunction} subclasses. This backend module does NOT declare a
 * Flink dependency. To bridge the two without a hard compile dependency here, this
 * class is intentionally Flink-API-free: it captures the tenant-isolation contract
 * (extract tenant → bind ThreadLocal → delegate → clear) that every concrete
 * operator MUST honor, and it is the single type the ArchUnit rule keys off.</p>
 *
 * <p><b>Concrete operator pattern (in the Flink job module):</b></p>
 * <pre>{@code
 * public final class DistrictAggregationJob extends KeyedProcessFunction<String, Event, Out> {
 *     private final TenantKeyedProcessFunctionDelegate<String, Event, Out> guard =
 *         TenantKeyedProcessFunctionDelegate.forFn(this::extractTenant, this::processInTenant);
 *
 *     @Override
 *     public void processElement(Event value, Context ctx, Collector<Out> out) {
 *         guard.run(value, out::collect);
 *     }
 * }
 * }</pre>
 *
 * <p>The ArchUnit rule ({@code TenantFunctionArchRule}) asserts that NO class named
 * {@code *Job} or extending {@code KeyedProcessFunction} exists without delegating
 * through {@link TenantKeyedProcessFunctionDelegate}. In this repo there are currently
 * no in-tree Flink operators (jobs run external), so the rule is a forward guard for
 * when MVP6 brings the Flink jobs back in-tree.</p>
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
