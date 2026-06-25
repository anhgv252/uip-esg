package com.uip.flink.structural;

import com.uip.flink.common.NgsiLdMessage;
import com.uip.flink.common.tenant.TenantKeyedProcessFunctionDelegate;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains per-sensor Welford online stddev state and emits only anomalous readings.
 *
 * <p>State is keyed by {@code sensorId} (deviceIdValue). For each incoming reading:</p>
 * <ol>
 *   <li>Updates the Welford running statistics for that sensor</li>
 *   <li>Emits the message downstream only if {@link WelfordStdDev#isAnomaly} returns true</li>
 * </ol>
 *
 * <p>Cold-start guard: {@link WelfordStdDev#MIN_SAMPLES} (1000) readings must accumulate
 * before anomaly detection activates, preventing false alerts at job start or restart.</p>
 *
 * <p><strong>BR-010:</strong> Downstream CEP and alert consumers treat emitted events as
 * operator-review candidates only — never auto-evacuate.</p>
 *
 * <p><strong>ADR-047 §1.3 — tenant isolation.</strong> {@code processElement} is routed
 * through {@link TenantKeyedProcessFunctionDelegate} so {@link
 * com.uip.flink.common.tenant.TenantContext} is bound to the record's tenant for the
 * duration of Welford state update / emit, and cleared in a {@code finally}. Records with
 * null/blank tenant are fail-closed dropped by the delegate (defense-in-depth; the upstream
 * filter already drops structural messages without a sensor type, and
 * {@code TenantBindingProcessFunction} guards window pipelines, but this operator is keyed
 * by sensorId only, so the per-record tenant binding is mandatory here). The Welford math,
 * the 4σ rule, the absolute floor, and the cold-start suppression are <em>unchanged</em> —
 * the delegate only adds the set/clear ThreadLocal lifecycle around them.</p>
 */
public class WelfordKeyedProcessFunction
        extends KeyedProcessFunction<String, NgsiLdMessage, NgsiLdMessage> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(WelfordKeyedProcessFunction.class);

    private transient ValueState<WelfordStdDev> welfordState;

    /**
     * Tenant guard (ADR-047 §1.3). Extractor = the message tenant; fail-closed drop on
     * null/blank. Held as a field so the ArchUnit rule can statically confirm the delegate
     * reference (every KeyedProcessFunction must reference the delegate). Initialised in
     * {@code open()} because {@code transient} fields are not restored on deserialization
     * on the task manager.
     */
    private transient TenantKeyedProcessFunctionDelegate<NgsiLdMessage, NgsiLdMessage> guard;

    @Override
    public void open(Configuration parameters) {
        guard = TenantKeyedProcessFunctionDelegate.forFn(NgsiLdMessage::getTenantId);
        welfordState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("welford-structural", WelfordStdDev.class));
    }

    @Override
    public void processElement(NgsiLdMessage msg, Context ctx, Collector<NgsiLdMessage> out)
            throws Exception {
        // ADR-047 §1.3 — bind tenant, run Welford under it, clear in finally.
        guard.run(msg, this::processInTenant, out::collect);
    }

    /** Tenant-scoped Welford update + emit. Untouched math; only the entry point changed. */
    private void processInTenant(NgsiLdMessage msg, java.util.function.Consumer<NgsiLdMessage> emit) {
        if (msg == null || msg.getMeta() == null) return;

        Double value = extractValue(msg);
        if (value == null) return;

        String sensorType = msg.getMeta().getSensorType();
        double absoluteFloor = StructuralThreshold.getWarningThreshold(sensorType);

        // ValueState.value()/update() throw IOException (checked). The delegate invokes the
        // processor via BiConsumer.accept (no checked exceptions), so rethrow as unchecked.
        // The delegate's finally still clears TenantContext, and processElement rethrows.
        WelfordStdDev welford;
        try {
            welford = welfordState.value();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed reading Welford state", e);
        }
        if (welford == null) {
            welford = new WelfordStdDev();
        }

        welford.update(value);
        try {
            welfordState.update(welford);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed updating Welford state", e);
        }

        if (shouldEmit(welford, value, absoluteFloor)) {
            LOG.debug("Structural anomaly: sensorId={} type={} value={} mean={} sigma={} n={}",
                    msg.getDeviceIdValue(), sensorType, value,
                    String.format("%.2f", welford.getMean()),
                    String.format("%.2f", welford.getStdDev()),
                    welford.getCount());
            emit.accept(msg);
        }
    }

    /**
     * Package-visible for unit testing — determines whether this reading should be emitted.
     * Returns true when Welford reports an anomaly above the absolute floor.
     */
    static boolean shouldEmit(WelfordStdDev welford, double value, double absoluteFloor) {
        return welford.isAnomaly(value, absoluteFloor);
    }

    private static Double extractValue(NgsiLdMessage msg) {
        if (msg.getMeasurementValues() == null || msg.getMeasurementValues().isEmpty()) return null;
        return msg.getMeasurementValues().get("value");
    }
}
