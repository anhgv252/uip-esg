package com.uip.backend.tenant.kafka;

import com.uip.backend.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAwareKafkaListenerTest {

    private final TestKafkaListener listener = new TestKafkaListener();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("should extract tenant_id from valid JSON")
    void shouldExtractFromValidJson() {
        String payload = "{\"sensor_id\":\"S-001\",\"tenant_id\":\"hcm\",\"value\":42.5}";
        assertThat(listener.callExtractTenantId(payload)).isEqualTo("hcm");
    }

    @Test
    @DisplayName("should fallback to default when tenant_id missing")
    void shouldFallbackWhenMissing() {
        String payload = "{\"sensor_id\":\"S-001\",\"value\":42.5}";
        assertThat(listener.callExtractTenantId(payload)).isEqualTo("default");
    }

    @Test
    @DisplayName("should fallback to default when tenant_id is blank")
    void shouldFallbackWhenBlank() {
        String payload = "{\"sensor_id\":\"S-001\",\"tenant_id\":\"  \"}";
        assertThat(listener.callExtractTenantId(payload)).isEqualTo("default");
    }

    @Test
    @DisplayName("should fallback to default when malformed JSON")
    void shouldFallbackWhenMalformed() {
        assertThat(listener.callExtractTenantId("not-json")).isEqualTo("default");
    }

    @Test
    @DisplayName("should fallback to default when null payload")
    void shouldFallbackWhenNull() {
        assertThat(listener.callExtractTenantId(null)).isEqualTo("default");
    }

    @Test
    @DisplayName("should fallback to default when empty payload")
    void shouldFallbackWhenEmpty() {
        assertThat(listener.callExtractTenantId("")).isEqualTo("default");
    }

    @Test
    @DisplayName("withTenantContext should set and clear TenantContext")
    void shouldSetAndClearContext() {
        AtomicReference<String> captured = new AtomicReference<>();

        listener.callWithTenantContext("hcm", () -> {
            captured.set(TenantContext.getCurrentTenant());
        });

        assertThat(captured.get()).isEqualTo("hcm");
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("withTenantContext should clear even on exception")
    void shouldClearOnException() {
        try {
            listener.callWithTenantContext("hcm", () -> {
                throw new RuntimeException("test error");
            });
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    private static class TestKafkaListener extends TenantAwareKafkaListener {
        String callExtractTenantId(String payload) {
            return extractTenantId(payload);
        }

        void callWithTenantContext(String tenantId, Runnable action) {
            withTenantContext(tenantId, action);
        }
    }
}
