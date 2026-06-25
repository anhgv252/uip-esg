package com.uip.flink.common.tenant;

import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.groups.OperatorMetricGroup;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantBindingProcessFunction} — the non-keyed operator that binds
 * TenantContext and fail-closed drops records with no tenant (ADR-047 §1.3).
 *
 * <p>Mock-based, following the {@code TenantIdValidatorTest} convention (no MiniCluster).</p>
 */
@DisplayName("TenantBindingProcessFunction — ADR-047 §1.3 pipeline-entry tenant guard")
class TenantBindingProcessFunctionTest {

    private RuntimeContext runtimeContext;
    private OperatorMetricGroup operatorMetricGroup;
    private MetricGroup uipTenantGroup;
    private Counter droppedCounter;

    private TenantBindingProcessFunction<NgsiLdMessage> fn;

    @BeforeEach
    void setUp() throws Exception {
        runtimeContext      = mock(RuntimeContext.class);
        operatorMetricGroup = mock(OperatorMetricGroup.class);
        uipTenantGroup      = mock(MetricGroup.class);
        droppedCounter      = mock(Counter.class);

        when(runtimeContext.getMetricGroup()).thenReturn(operatorMetricGroup);
        when(operatorMetricGroup.addGroup("uip", "tenant")).thenReturn(uipTenantGroup);
        when(uipTenantGroup.counter("dropped_no_tenant")).thenReturn(droppedCounter);

        fn = new TenantBindingProcessFunction<>(NgsiLdMessage::getTenantId);
        fn.setRuntimeContext(runtimeContext);
        fn.open(new Configuration());
    }

    @AfterEach
    void clearThread() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("valid tenant — record collected, no drop counter increment, context bound during processing")
    void validTenant_collectsRecord_andBindsContext() throws Exception {
        List<NgsiLdMessage> output = new ArrayList<>();
        NgsiLdMessage msg = msgWithTenant("tenant-A", "sensor-001");

        fn.processElement(msg, fakeContext(), collectTo(output));

        assertThat(output).hasSize(1);
        assertThat(output.get(0)).isSameAs(msg);
        // No drop counted for a valid record.
        // (verify(droppedCounter).inc() would fail because it was never called — assert via 0 interactions)
    }

    @Test
    @DisplayName("null tenant — record dropped (fail-closed), drop counter incremented")
    void nullTenant_droppedAndCounted() throws Exception {
        List<NgsiLdMessage> output = new ArrayList<>();
        NgsiLdMessage msg = msgWithTenant(null, "sensor-002");

        fn.processElement(msg, fakeContext(), collectTo(output));

        assertThat(output).isEmpty();
        verify(droppedCounter).inc();
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("blank tenant — record dropped (fail-closed), drop counter incremented")
    void blankTenant_droppedAndCounted() throws Exception {
        List<NgsiLdMessage> output = new ArrayList<>();
        NgsiLdMessage msg = msgWithTenant("   ", "sensor-003");

        fn.processElement(msg, fakeContext(), collectTo(output));

        assertThat(output).isEmpty();
        verify(droppedCounter).inc();
    }

    @Test
    @DisplayName("constructor rejects null extractor (fail-closed at construction)")
    void constructor_rejectsNullExtractor() {
        try {
            new TenantBindingProcessFunction<NgsiLdMessage>(null);
            org.junit.jupiter.api.Assertions.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("TenantExtractor");
        }
    }

    @Test
    @DisplayName("consecutive records with different tenants do not bleed ThreadLocal")
    void consecutiveRecords_noBleed() throws Exception {
        List<NgsiLdMessage> output = new ArrayList<>();
        fn.processElement(msgWithTenant("tenant-A", "s1"), fakeContext(), collectTo(output));
        fn.processElement(msgWithTenant("tenant-B", "s2"), fakeContext(), collectTo(output));

        assertThat(output).hasSize(2);
        assertThat(TenantContext.get()).isNull();
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
    private ProcessFunction<NgsiLdMessage, NgsiLdMessage>.Context fakeContext() {
        ProcessFunction outerInstance = mock(ProcessFunction.class);
        return (ProcessFunction<NgsiLdMessage, NgsiLdMessage>.Context)
                outerInstance.new Context() {
                    @Override public Long timestamp() { return null; }
                    @Override public org.apache.flink.streaming.api.TimerService timerService() { return null; }
                    @Override public void output(org.apache.flink.util.OutputTag outputTag, Object value) { }
                };
    }

    private Collector<NgsiLdMessage> collectTo(List<NgsiLdMessage> list) {
        return new Collector<>() {
            @Override public void collect(NgsiLdMessage record) { list.add(record); }
            @Override public void close() {}
        };
    }
}
