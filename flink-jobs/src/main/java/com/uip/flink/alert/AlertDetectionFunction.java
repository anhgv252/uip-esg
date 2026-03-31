package com.uip.flink.alert;

import com.uip.flink.environment.EnvironmentReading;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Sliding-window alert detection function.
 * Emits AlertEvents when average metric exceeds configured thresholds.
 */
public class AlertDetectionFunction
        extends ProcessWindowFunction<EnvironmentReading, AlertEvent, String, TimeWindow> {

    private static final Logger LOG = LoggerFactory.getLogger(AlertDetectionFunction.class);

    // Configurable alert rules (in production: loaded via YAML or DB)
    private static final List<AlertRule> RULES = List.of(
            new AlertRule("aqi", ">",  150, "WARNING"),
            new AlertRule("aqi", ">",  200, "CRITICAL"),
            new AlertRule("aqi", ">",  300, "EMERGENCY"),
            new AlertRule("pm25", ">",  55, "WARNING"),
            new AlertRule("pm25", ">", 150, "CRITICAL")
    );

    @Override
    public void process(
            String sensorId,
            Context context,
            Iterable<EnvironmentReading> readings,
            Collector<AlertEvent> out
    ) {
        double sumAqi = 0, sumPm25 = 0;
        long count = 0;

        for (EnvironmentReading r : readings) {
            if (r.getAqi() != null) sumAqi += r.getAqi();
            if (r.getPm25() != null) sumPm25 += r.getPm25();
            count++;
        }

        if (count == 0) return;

        double avgAqi  = sumAqi  / count;
        double avgPm25 = sumPm25 / count;

        Instant windowEnd = Instant.ofEpochMilli(context.window().getEnd());

        checkAndEmit(sensorId, "aqi",  avgAqi,  windowEnd, out);
        checkAndEmit(sensorId, "pm25", avgPm25, windowEnd, out);
    }

    private void checkAndEmit(String sensorId, String measureType, double avgValue,
                               Instant detectedAt, Collector<AlertEvent> out) {
        // Emit only the highest severity matching rule
        AlertRule matched = null;
        for (AlertRule rule : RULES) {
            if (rule.matches(measureType, avgValue)) {
                if (matched == null || severityRank(rule.getSeverity()) > severityRank(matched.getSeverity())) {
                    matched = rule;
                }
            }
        }
        if (matched != null) {
            AlertEvent event = new AlertEvent(
                    sensorId, "ENVIRONMENT", measureType,
                    avgValue, matched.getThreshold(), matched.getSeverity(), detectedAt);
            LOG.warn("Alert detected sensorId={} measure={} avg={} severity={}",
                    sensorId, measureType, avgValue, matched.getSeverity());
            out.collect(event);
        }
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "WARNING"   -> 1;
            case "CRITICAL"  -> 2;
            case "EMERGENCY" -> 3;
            default          -> 0;
        };
    }
}
