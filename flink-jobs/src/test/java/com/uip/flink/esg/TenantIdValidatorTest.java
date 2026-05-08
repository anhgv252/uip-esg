package com.uip.flink.esg;

import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.groups.OperatorMetricGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("Valid tenantId — message passed through unchanged, validCount incremented")
    void validTenantId_passesThrough() throws Exception {
        NgsiLdMessage msg = msgWithTenant("hcm", "sensor-001");

        NgsiLdMessage result = validator.map(msg);

        assertThat(result).isSameAs(msg);
        verify(validCounter).inc();
    }

    @Test
    @DisplayName("null tenantId — TelemetryValidationException thrown, missingCount incremented")
    void nullTenantId_throwsException() {
        NgsiLdMessage msg = msgWithTenant(null, "sensor-002");

        assertThatThrownBy(() -> validator.map(msg))
                .isInstanceOf(TelemetryValidationException.class)
                .hasMessageContaining("tenant_id");

        verify(missingCounter).inc();
    }

    @Test
    @DisplayName("blank tenantId — TelemetryValidationException thrown, missingCount incremented")
    void blankTenantId_throwsException() {
        NgsiLdMessage msg = msgWithTenant("  ", "sensor-003");

        assertThatThrownBy(() -> validator.map(msg))
                .isInstanceOf(TelemetryValidationException.class);

        verify(missingCounter).inc();
    }

    @Test
    @DisplayName("Exception carries MISSING_TENANT_ID error code and sensorId")
    void exceptionHasCorrectErrorCodeAndSensorId() {
        NgsiLdMessage msg = msgWithTenant(null, "sensor-004");

        assertThatThrownBy(() -> validator.map(msg))
                .isInstanceOf(TelemetryValidationException.class)
                .satisfies(ex -> {
                    TelemetryValidationException tve = (TelemetryValidationException) ex;
                    assertThat(tve.getErrorCode()).isEqualTo("MISSING_TENANT_ID");
                    assertThat(tve.getSensorId()).isEqualTo("sensor-004");
                });
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

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
}
