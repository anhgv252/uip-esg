package com.uip.flink.common.tenant;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-keyed {@link ProcessFunction} that binds the {@link TenantContext} for each
 * valid record and fail-closed drops records whose tenant cannot be extracted.
 *
 * <p><b>Purpose.</b> Window-based pipelines (e.g. {@code DistrictAggregationJob})
 * achieve tenant isolation through the {@code keyBy} composite key, so they do not
 * host a {@code KeyedProcessFunction} to wrap. This thin operator is inserted
 * <em>right after</em> the source/filter and <em>before</em> {@code keyBy}, so that
 * every record downstream of it has a tenant bound for the duration of its
 * processing. It does NOT alter the record, does NOT touch the window/aggregate
 * logic, and does NOT break G1 window-batching.</p>
 *
 * <p><b>Fail-closed.</b> Records with null/blank tenant (or whose extractor throws)
 * are dropped here and counted via the {@code uip.tenant.dropped_no_tenant} metric.
 * This is the same contract the {@link TenantKeyedProcessFunctionDelegate} enforces
 * inside keyed operators — surfaced at the pipeline entry point for window jobs.</p>
 *
 * <p><b>ThreadLocal lifecycle.</b> The delegate sets {@link TenantContext} on the
 * current task-manager thread before the record is collected and clears it in a
 * {@code finally}. Downstream operators that run on the same task slot
 * (chained operators) can read {@link TenantContext#get()} inside their
 * {@code processElement}. Note: for {@code keyBy} the record crosses a network /
 * shuffle boundary, so the {@link TenantContext} does not transit — but the
 * composite key already carries {@code tenantId}, preserving isolation.</p>
 *
 * @param <IN> record type
 */
public final class TenantBindingProcessFunction<IN> extends ProcessFunction<IN, IN> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(TenantBindingProcessFunction.class);

    private final TenantKeyedProcessFunction.TenantExtractor<IN> extractor;

    private transient TenantKeyedProcessFunctionDelegate<IN, IN> guard;
    private transient Counter droppedNoTenant;

    public TenantBindingProcessFunction(TenantKeyedProcessFunction.TenantExtractor<IN> extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException(
                "TenantExtractor must not be null (ADR-047, TenantBindingProcessFunction)");
        }
        this.extractor = extractor;
    }

    @Override
    public void open(Configuration parameters) {
        this.guard = TenantKeyedProcessFunctionDelegate.forFn(extractor);
        this.droppedNoTenant = getRuntimeContext()
                .getMetricGroup()
                .addGroup("uip", "tenant")
                .counter("dropped_no_tenant");
    }

    @Override
    public void processElement(IN record, Context ctx, Collector<IN> out) throws Exception {
        long before = guard.droppedNoTenant();
        guard.run(record, (rec, emit) -> emit.accept(rec), out::collect);
        if (guard.droppedNoTenant() > before) {
            droppedNoTenant.inc();
            LOG.warn("TenantBindingProcessFunction dropped record — no tenant (fail-closed, ADR-047)");
        }
    }
}
