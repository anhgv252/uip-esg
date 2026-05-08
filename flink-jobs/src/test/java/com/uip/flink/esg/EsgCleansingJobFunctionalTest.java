package com.uip.flink.esg;

import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flink test-harness tests for the EsgCleansingJob pipeline.
 * Uses a local StreamExecutionEnvironment with bounded fromCollection() sources —
 * no Kafka or JDBC required.
 */
class EsgCleansingJobFunctionalTest {

    // Static sinks shared across Flink task threads (parallelism=1, single-threaded)
    private static final List<NgsiLdMessage> VALID_SINK =
            Collections.synchronizedList(new ArrayList<>());
    private static final List<Map<String, Object>> ERROR_SINK =
            Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void clearSinks() {
        VALID_SINK.clear();
        ERROR_SINK.clear();
    }

    @AfterEach
    void clearSinksAfter() {
        VALID_SINK.clear();
        ERROR_SINK.clear();
    }

    @Test
    @DisplayName("Mixed input: valid message → main output, missing tenant_id → error side output")
    void mixedMessages_routedCorrectly() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<NgsiLdMessage> validated = env
                .fromData(
                        message("hcm", "sensor-v"),
                        message(null, "sensor-x"))
                .process(new TenantIdValidator());

        validated.addSink(new ValidSink());
        validated.getSideOutput(TenantIdValidator.ERROR_TAG).addSink(new ErrorSink());

        env.execute("mixed-messages-test");

        assertThat(VALID_SINK).hasSize(1);
        assertThat(VALID_SINK.get(0).getDeviceIdValue()).isEqualTo("sensor-v");

        assertThat(ERROR_SINK).hasSize(1);
        assertThat(ERROR_SINK.get(0).get("errorCode")).isEqualTo("MISSING_TENANT_ID");
        assertThat(ERROR_SINK.get(0).get("sensorId")).isEqualTo("sensor-x");
    }

    @Test
    @DisplayName("All valid messages → all reach main output, no errors emitted")
    void allValid_noErrors() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<NgsiLdMessage> validated = env
                .fromData(
                        message("hcm",   "s-1"),
                        message("hanoi", "s-2"),
                        message("dn",    "s-3"))
                .process(new TenantIdValidator());

        validated.addSink(new ValidSink());
        validated.getSideOutput(TenantIdValidator.ERROR_TAG).addSink(new ErrorSink());

        env.execute("all-valid-test");

        assertThat(VALID_SINK).hasSize(3);
        assertThat(ERROR_SINK).isEmpty();
    }

    @Test
    @DisplayName("All messages missing tenant_id → none reach main output, all errors emitted")
    void allInvalid_allErrors() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<NgsiLdMessage> validated = env
                .fromData(
                        message(null, "s-a"),
                        message("  ", "s-b"))
                .process(new TenantIdValidator());

        validated.addSink(new ValidSink());
        validated.getSideOutput(TenantIdValidator.ERROR_TAG).addSink(new ErrorSink());

        env.execute("all-invalid-test");

        assertThat(VALID_SINK).isEmpty();
        assertThat(ERROR_SINK).hasSize(2);
        assertThat(ERROR_SINK).allSatisfy(err ->
                assertThat(err.get("errorCode")).isEqualTo("MISSING_TENANT_ID"));
    }

    @Test
    @DisplayName("Error output contains both errorCode and sensorId fields")
    void errorOutput_hasRequiredFields() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<NgsiLdMessage> validated = env
                .fromData(message(null, "bad-sensor-99"))
                .process(new TenantIdValidator());

        validated.addSink(new ValidSink());
        validated.getSideOutput(TenantIdValidator.ERROR_TAG).addSink(new ErrorSink());

        env.execute("error-fields-test");

        assertThat(ERROR_SINK).hasSize(1);
        Map<String, Object> err = ERROR_SINK.get(0);
        assertThat(err).containsKeys("errorCode", "sensorId", "message", "detectedAt");
        assertThat(err.get("errorCode")).isEqualTo("MISSING_TENANT_ID");
        assertThat(err.get("sensorId")).isEqualTo("bad-sensor-99");
    }

    @Test
    @DisplayName("Empty stream — no output emitted in either channel")
    void emptyStream_noOutput() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        env.setParallelism(1);

        SingleOutputStreamOperator<NgsiLdMessage> validated = env
                .fromCollection(Collections.emptyList(), TypeInformation.of(NgsiLdMessage.class))
                .process(new TenantIdValidator());

        validated.addSink(new ValidSink());
        validated.getSideOutput(TenantIdValidator.ERROR_TAG).addSink(new ErrorSink());

        env.execute("empty-stream-test");

        assertThat(VALID_SINK).isEmpty();
        assertThat(ERROR_SINK).isEmpty();
    }

    // ─── Sinks ───────────────────────────────────────────────────────────────

    static class ValidSink implements SinkFunction<NgsiLdMessage> {
        @Override
        public void invoke(NgsiLdMessage value, Context context) {
            VALID_SINK.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    static class ErrorSink implements SinkFunction<Map<String, Object>> {
        @Override
        public void invoke(Map<String, Object> value, Context context) {
            ERROR_SINK.add(value);
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private NgsiLdMessage message(String tenantId, String deviceId) {
        NgsiLdMessage msg = new NgsiLdMessage();
        NgsiLdMessage.NgsiLdProperty<String> devIdProp = new NgsiLdMessage.NgsiLdProperty<>();
        devIdProp.setValue(deviceId);
        msg.setDeviceId(devIdProp);
        NgsiLdMessage.Meta meta = new NgsiLdMessage.Meta();
        meta.setTenantId(tenantId);
        msg.setMeta(meta);
        return msg;
    }
}
