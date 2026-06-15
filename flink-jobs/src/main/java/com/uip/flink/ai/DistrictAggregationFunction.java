package com.uip.flink.ai;

import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.List;

/**
 * M4-AI-01: Aggregates {@link NgsiLdMessage} readings for one
 * (tenant, district, sensorType) key within a tumbling window into a single
 * {@link DistrictAggregation}.
 *
 * <p>Two cooperating pieces (Flink's
 * {@code window().aggregate(aggFunction, processFunction)} idiom):</p>
 * <ol>
 *   <li>{@link Accumulator} maintains running count, sum, max, and a capped
 *       list of sensor snapshots (eviction is FIFO once the cap is reached,
 *       keeping the most recent readings).</li>
 *   <li>{@link DistrictAggregationFunction} is the incremental
 *       {@link AggregateFunction}; {@link WindowFinalizer} attaches the
 *       window bounds and emits the final record.</li>
 * </ol>
 *
 * <p>The sensor value is read from {@code measurements.value} — the same
 * convention used by {@code VibrationAnomalyJob.extractMeasurementValue}.</p>
 */
public class DistrictAggregationFunction
        implements AggregateFunction<NgsiLdMessage, DistrictAggregationFunction.Accumulator, DistrictAggregationFunction.Accumulator> {

    private static final long serialVersionUID = 1L;

    /** Cap on retained sensor snapshots to bound state size. */
    private final int maxSensors;

    public DistrictAggregationFunction(int maxSensors) {
        this.maxSensors = Math.max(0, maxSensors);
    }

    // ─── AggregateFunction ────────────────────────────────────────────────────

    @Override
    public Accumulator createAccumulator() {
        return new Accumulator();
    }

    @Override
    public Accumulator add(NgsiLdMessage msg, Accumulator acc) {
        Double value = extractMeasurementValue(msg);
        if (value == null) {
            return acc; // skip readings without a numeric "value"
        }
        acc.count++;
        acc.sum += value;
        if (acc.count == 1 || value > acc.max) {
            acc.max = value;
        }
        if (acc.tenantId == null) {
            acc.tenantId = msg.getTenantId();
        }
        if (acc.sensorType == null && msg.getMeta() != null) {
            acc.sensorType = msg.getMeta().getSensorType();
        }
        if (maxSensors > 0) {
            if (acc.snapshots.size() >= maxSensors) {
                // FIFO eviction — keep most recent readings
                acc.snapshots.remove(0);
            }
            acc.snapshots.add(new DistrictAggregation.SensorSnapshot(
                    msg.getDeviceIdValue(), value, msg.getObservedAtMillis()));
        }
        return acc;
    }

    @Override
    public Accumulator getResult(Accumulator acc) {
        return acc;
    }

    @Override
    public Accumulator merge(Accumulator a, Accumulator b) {
        // Note: snapshot lists are merged best-effort (capped); stats are exact.
        Accumulator merged = new Accumulator();
        merged.count = a.count + b.count;
        merged.sum = a.sum + b.sum;
        merged.max = Math.max(a.max, b.max);
        merged.tenantId = a.tenantId != null ? a.tenantId : b.tenantId;
        merged.sensorType = a.sensorType != null ? a.sensorType : b.sensorType;
        List<DistrictAggregation.SensorSnapshot> combined = new ArrayList<>(a.snapshots);
        combined.addAll(b.snapshots);
        if (maxSensors > 0 && combined.size() > maxSensors) {
            combined = new ArrayList<>(
                    combined.subList(combined.size() - maxSensors, combined.size()));
        }
        merged.snapshots = combined;
        return merged;
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /** Extract the primary measurement value (mirrors VibrationAnomalyJob convention). */
    static Double extractMeasurementValue(NgsiLdMessage msg) {
        if (msg == null || msg.getMeasurementValues() == null || msg.getMeasurementValues().isEmpty()) {
            return null;
        }
        return msg.getMeasurementValues().get("value");
    }

    /** Mutable accumulator state. */
    static class Accumulator implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        int count = 0;
        double sum = 0.0;
        double max = Double.NEGATIVE_INFINITY;
        String tenantId = null;
        String sensorType = null;
        List<DistrictAggregation.SensorSnapshot> snapshots = new ArrayList<>();
    }

    /**
     * Finalizes the window: converts the accumulator to a {@link DistrictAggregation}
     * with window bounds attached. The {@code districtCode} is passed in via the
     * keyed window context (extracted at {@code keyBy} time) so the accumulator
     * does not need to store it.
     */
    public static class WindowFinalizer
            extends ProcessWindowFunction<Accumulator, DistrictAggregation, DistrictKey, TimeWindow> {

        private static final long serialVersionUID = 1L;

        @Override
        public void process(DistrictKey key,
                            Context ctx,
                            Iterable<Accumulator> elements,
                            Collector<DistrictAggregation> out) {
            Accumulator acc = elements.iterator().next();
            if (acc.count == 0) {
                return; // no valid readings in this window
            }
            TimeWindow w = ctx.window();
            out.collect(new DistrictAggregation(
                    acc.tenantId,
                    key.getDistrictCode(),
                    acc.sensorType != null ? acc.sensorType : key.getSensorType(),
                    acc.count,
                    acc.max,
                    acc.sum / acc.count,
                    w.getStart(),
                    w.getEnd(),
                    acc.snapshots
            ));
        }
    }
}
