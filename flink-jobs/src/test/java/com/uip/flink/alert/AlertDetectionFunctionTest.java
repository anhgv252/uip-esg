package com.uip.flink.alert;

import com.uip.flink.environment.EnvironmentReading;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertDetectionFunctionTest {

    private AlertDetectionFunction function;
    private List<AlertEvent> output;
    private TimeWindow window;

    @BeforeEach
    void setUp() {
        function = new AlertDetectionFunction();
        output = new ArrayList<>();
        window = new TimeWindow(
                Instant.now().minusSeconds(300).toEpochMilli(),
                Instant.now().toEpochMilli()
        );
    }

    @Test
    @DisplayName("AQI avg > 200 emits CRITICAL alert")
    void aqiAvgAbove200_emitsCritical() throws Exception {
        List<EnvironmentReading> readings = List.of(
                reading("sensor-1", 210.0, 40.0),
                reading("sensor-1", 220.0, 45.0),
                reading("sensor-1", 205.0, 38.0)
        );

        function.process("sensor-1", fakeContext(), readings, collectTo(output));

        assertThat(output).anySatisfy(alert -> {
            assertThat(alert.getSensorId()).isEqualTo("sensor-1");
            assertThat(alert.getMeasureType()).isEqualTo("aqi");
            assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
        });
    }

    @Test
    @DisplayName("AQI avg > 300 emits EMERGENCY alert — highest severity wins")
    void aqiAvgAbove300_emitsEmergency() throws Exception {
        List<EnvironmentReading> readings = List.of(
                reading("sensor-1", 310.0, 60.0),
                reading("sensor-1", 320.0, 65.0)
        );

        function.process("sensor-1", fakeContext(), readings, collectTo(output));

        assertThat(output).anySatisfy(alert -> {
            assertThat(alert.getMeasureType()).isEqualTo("aqi");
            assertThat(alert.getSeverity()).isEqualTo("EMERGENCY");
        });
    }

    @Test
    @DisplayName("AQI avg between 150-200 emits WARNING")
    void aqiAvgBetween150and200_emitsWarning() throws Exception {
        List<EnvironmentReading> readings = List.of(
                reading("sensor-2", 160.0, 30.0),
                reading("sensor-2", 155.0, 28.0)
        );

        function.process("sensor-2", fakeContext(), readings, collectTo(output));

        assertThat(output).anySatisfy(alert ->
                assertThat(alert.getSeverity()).isEqualTo("WARNING"));
    }

    @Test
    @DisplayName("AQI avg <= 150 emits no alert")
    void aqiAvgBelow150_emitsNoAlert() throws Exception {
        List<EnvironmentReading> readings = List.of(
                reading("sensor-3", 100.0, 20.0),
                reading("sensor-3", 140.0, 25.0)
        );

        function.process("sensor-3", fakeContext(), readings, collectTo(output));

        assertThat(output.stream().filter(a -> "aqi".equals(a.getMeasureType()))).isEmpty();
    }

    @Test
    @DisplayName("Empty readings window emits no alert")
    void emptyWindow_emitsNoAlert() throws Exception {
        function.process("sensor-4", fakeContext(), List.of(), collectTo(output));
        assertThat(output).isEmpty();
    }

    @Test
    @DisplayName("PM2.5 avg > 55 emits WARNING")
    void pm25AvgAbove55_emitsWarning() throws Exception {
        List<EnvironmentReading> readings = List.of(
                reading("sensor-5", 100.0, 60.0),
                reading("sensor-5", 100.0, 70.0)
        );

        function.process("sensor-5", fakeContext(), readings, collectTo(output));

        assertThat(output).anySatisfy(alert -> {
            assertThat(alert.getMeasureType()).isEqualTo("pm25");
            assertThat(alert.getSeverity()).isIn("WARNING", "CRITICAL");
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private EnvironmentReading reading(String sensorId, Double aqi, Double pm25) {
        return new EnvironmentReading(sensorId, Instant.now(), aqi, pm25, null,
                null, null, null, null, null, null, "{}");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ProcessWindowFunction<EnvironmentReading, AlertEvent, String, TimeWindow>.Context fakeContext() {
        // Context is a non-static abstract inner class of ProcessWindowFunction.
        // We create it via a raw-typed ProcessWindowFunction mock as the enclosing instance,
        // then cast. The output() method must use raw types to satisfy erasure.
        ProcessWindowFunction outerInstance = mock(ProcessWindowFunction.class);
        return (ProcessWindowFunction<EnvironmentReading, AlertEvent, String, TimeWindow>.Context)
                outerInstance.new Context() {
                    @Override public TimeWindow window() { return window; }
                    @Override public long currentProcessingTime() { return System.currentTimeMillis(); }
                    @Override public long currentWatermark() { return Long.MIN_VALUE; }
                    @Override public org.apache.flink.api.common.state.KeyedStateStore windowState() { return null; }
                    @Override public org.apache.flink.api.common.state.KeyedStateStore globalState() { return null; }
                    @Override public void output(org.apache.flink.util.OutputTag tag, Object value) {}
                };
    }

    private Collector<AlertEvent> collectTo(List<AlertEvent> list) {
        return new Collector<>() {
            @Override public void collect(AlertEvent record) { list.add(record); }
            @Override public void close() {}
        };
    }
}
