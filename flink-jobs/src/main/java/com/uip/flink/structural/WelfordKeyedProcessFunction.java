package com.uip.flink.structural;

import com.uip.flink.common.NgsiLdMessage;
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
 */
public class WelfordKeyedProcessFunction
        extends KeyedProcessFunction<String, NgsiLdMessage, NgsiLdMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(WelfordKeyedProcessFunction.class);

    private transient ValueState<WelfordStdDev> welfordState;

    @Override
    public void open(Configuration parameters) {
        welfordState = getRuntimeContext().getState(
                new ValueStateDescriptor<>("welford-structural", WelfordStdDev.class));
    }

    @Override
    public void processElement(NgsiLdMessage msg, Context ctx, Collector<NgsiLdMessage> out)
            throws Exception {
        if (msg == null || msg.getMeta() == null) return;

        Double value = extractValue(msg);
        if (value == null) return;

        String sensorType = msg.getMeta().getSensorType();
        double absoluteFloor = StructuralThreshold.getWarningThreshold(sensorType);

        WelfordStdDev welford = welfordState.value();
        if (welford == null) {
            welford = new WelfordStdDev();
        }

        welford.update(value);
        welfordState.update(welford);

        if (shouldEmit(welford, value, absoluteFloor)) {
            LOG.debug("Structural anomaly: sensorId={} type={} value={} mean={} sigma={} n={}",
                    msg.getDeviceIdValue(), sensorType, value,
                    String.format("%.2f", welford.getMean()),
                    String.format("%.2f", welford.getStdDev()),
                    welford.getCount());
            out.collect(msg);
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
