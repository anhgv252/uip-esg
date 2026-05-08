package com.uip.flink.esg;

import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.groups.OperatorMetricGroup;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantIdValidatorTest {

    private RuntimeContext runtimeContext;
    private OperatorMetricGroup operatorMetricGroup;
    private MetricGroup uipEsgGroup;
    private Counter validCounter;
    private Counter missingCounter;

    private TenantIdValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        runtimeContext      = mock(RuntimeContext.class);
        operatorMetricGroup = mock(OperatorMetricGroup.class);
        uipEsgGroup         = mock(MetricGroup.class);
        validCounter        = mock(Counter.class);
        missingCounter      = mock(Counter.class);

        when(runtimeContext.getMetricGroup()).thenReturn(operatorMetricGroup);
        when(operatorMetricGroup.addGroup("uip", "esg")).thenReturn(uipEsgGroup);
        when(uipEsgGroup.counter("tenant_id_valid_count")).thenReturn(validCounter);
        when(uipEsgGroup.counter("tenant_id_missing_count")).thenReturn(missingCounter);

        validator = new TenantIdValidator();
        validator.setRuntimeContext(runtimeContext);
        validator.open(new Configuration());
    }

    @Test
    @DisplayName("Valid tenantId — message collected to main output, validCount incremented")
    void validTenantId_collectsToMainOutput() throws Exception {
        List<NgsiLdMessage> output = new ArrayList<>();
        NgsiLdMessage msg = msgWithTenant("hcm", "sensor-001");

        validator.processElement(msg, fakeContext(new ArrayList<>()), collectTo(output));

        assertThat(output).hasSize(1);
        assertThat(output.get(0)).isSameAs(msg);
        verify(validCounter).inc();
    }

    @Test
    @DisplayName("null tenantId — routed to error side output, missingCount incremented")
    void nullTenantId_routedToErrorSideOutput() throws Exception {
        List<Map<String, Object>> errors = new ArrayList<>();
        NgsiLdMessage msg = msgWithTenant(null, "sensor-002");

        validator.processElement(msg, fakeContext(errors), collectTo(new ArrayList<>()));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).get("errorCode")).isEqualTo("MISSING_TENANT_ID");
        verify(missingCounter).inc();
    }

    @Test
    @DisplayName("blank tenantId — routed to error side output, missingCount incremented")
    void blankTenantId_routedToErrorSideOutput() throws Exception {
        List<Map<String, Object>> errors = new ArrayList<>();
        NgsiLdMessage msg = msgWithTenant("  ", "sensor-003");

        validator.processElement(msg, fakeContext(errors), collectTo(new ArrayList<>()));

        assertThat(errors).hasSize(1);
        verify(missingCounter).inc();
    }

    @Test
    @DisplayName("Error output contains MISSING_TENANT_ID errorCode and correct sensorId")
    void errorOutput_containsCorrectFields() throws Exception {
        List<Map<String, Object>> errors = new ArrayList<>();
        NgsiLdMessage msg = msgWithTenant(null, "sensor-004");

        validator.processElement(msg, fakeContext(errors), collectTo(new ArrayList<>()));

        assertThat(errors.get(0).get("errorCode")).isEqualTo("MISSING_TENANT_ID");
        assertThat(errors.get(0).get("sensorId")).isEqualTo("sensor-004");
    }

    @Test
    @DisplayName("Valid message produces no error side output")
    void validMessage_noErrorOutput() throws Exception {
        List<Map<String, Object>> errors = new ArrayList<>();
        NgsiLdMessage msg = msgWithTenant("hcm", "sensor-005");

        validator.processElement(msg, fakeContext(errors), collectTo(new ArrayList<>()));

        assertThat(errors).isEmpty();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private NgsiLdMessage msgWithTenant(String tenantId, String deviceId) {
        NgsiLdMessage msg = new NgsiLdMessage();
        NgsiLdMessage.NgsiLdProperty<String> devIdProp = new NgsiLdMessage.NgsiLdProperty<>();
        devIdProp.setValue(deviceId);
        msg.setDeviceId(devIdProp);
        NgsiLdMessage.Meta meta = new NgsiLdMessage.Meta();
        meta.setTenantId(tenantId);
        msg.setMeta(meta);
        return msg;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ProcessFunction<NgsiLdMessage, NgsiLdMessage>.Context fakeContext(
            List<Map<String, Object>> errorCapture) {
        // Context is a non-static abstract inner class of ProcessFunction.
        // Create via a raw-typed mock as the enclosing instance, then cast.
        ProcessFunction outerInstance = mock(ProcessFunction.class);
        return (ProcessFunction<NgsiLdMessage, NgsiLdMessage>.Context)
                outerInstance.new Context() {
                    @Override public Long timestamp() { return null; }
                    @Override public org.apache.flink.streaming.api.TimerService timerService() { return null; }
                    @Override
                    @SuppressWarnings("unchecked")
                    public void output(OutputTag outputTag, Object value) {
                        if (TenantIdValidator.ERROR_TAG.equals(outputTag)) {
                            errorCapture.add((Map<String, Object>) value);
                        }
                    }
                };
    }

    private Collector<NgsiLdMessage> collectTo(List<NgsiLdMessage> list) {
        return new Collector<>() {
            @Override public void collect(NgsiLdMessage record) { list.add(record); }
            @Override public void close() {}
        };
    }
}
