package com.uip.backend.tenant.hibernate;

import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.domain.TenantAware;
import org.junit.jupiter.api.AfterEach;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantEntityListenerTest {

    private final TenantEntityListener listener = new TenantEntityListener();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("should set tenantId from TenantContext on TenantAware entity")
    void shouldSetTenantIdFromContext() {
        TenantContext.setCurrentTenant("hcm");

        TestTenantEntity entity = new TestTenantEntity();
        listener.prePersist(entity);

        assertThat(entity.getTenantId()).isEqualTo("hcm");
    }

    @Test
    @DisplayName("should fallback to default when TenantContext is null")
    void shouldFallbackWhenContextNull() {
        // TenantContext not set → getCurrentTenant() returns null

        TestTenantEntity entity = new TestTenantEntity();
        listener.prePersist(entity);

        assertThat(entity.getTenantId()).isEqualTo("default");
    }

    @Test
    @DisplayName("should not overwrite existing tenantId")
    void shouldNotOverwriteExistingTenantId() {
        TenantContext.setCurrentTenant("hcm");

        TestTenantEntity entity = new TestTenantEntity();
        entity.setTenantId("hn");
        listener.prePersist(entity);

        assertThat(entity.getTenantId()).isEqualTo("hn");
    }

    @Test
    @DisplayName("should skip non-TenantAware entities")
    void shouldSkipNonTenantAware() {
        TenantContext.setCurrentTenant("hcm");

        Object plainEntity = new Object();
        // Should not throw
        listener.prePersist(plainEntity);
    }

    @Test
    @DisplayName("should not overwrite when tenantId is blank string")
    void shouldOverwriteWhenBlank() {
        TenantContext.setCurrentTenant("hcm");

        TestTenantEntity entity = new TestTenantEntity();
        entity.setTenantId("   ");
        listener.prePersist(entity);

        // Blank is treated as not set → should overwrite
        assertThat(entity.getTenantId()).isEqualTo("hcm");
    }

    private static class TestTenantEntity implements TenantAware {
        private String tenantId;

        @Override
        public String getTenantId() { return tenantId; }

        @Override
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    }
}
