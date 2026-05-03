package com.uip.backend.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("setCurrentTenant")
    class SetCurrentTenant {

        @Test
        @DisplayName("should store tenant id")
        void shouldStoreTenantId() {
            TenantContext.setCurrentTenant("hcm");
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("hcm");
        }

        @Test
        @DisplayName("should fallback to default when null")
        void shouldFallbackWhenNull() {
            TenantContext.setCurrentTenant(null);
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("default");
        }

        @Test
        @DisplayName("should fallback to default when blank")
        void shouldFallbackWhenBlank() {
            TenantContext.setCurrentTenant("   ");
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("default");
        }

        @Test
        @DisplayName("should fallback to default when empty")
        void shouldFallbackWhenEmpty() {
            TenantContext.setCurrentTenant("");
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("default");
        }

        @Test
        @DisplayName("should overwrite previous value")
        void shouldOverwrite() {
            TenantContext.setCurrentTenant("hcm");
            TenantContext.setCurrentTenant("hn");
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("hn");
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("should remove tenant context")
        void shouldRemoveContext() {
            TenantContext.setCurrentTenant("hcm");
            TenantContext.clear();
            assertThat(TenantContext.getCurrentTenant()).isNull();
        }
    }

    @Nested
    @DisplayName("getDefaultTenant")
    class GetDefaultTenant {

        @Test
        @DisplayName("should return 'default'")
        void shouldReturnDefault() {
            assertThat(TenantContext.getDefaultTenant()).isEqualTo("default");
        }
    }

    @Nested
    @DisplayName("Thread isolation")
    class ThreadIsolation {

        @Test
        @DisplayName("should not leak across threads")
        void shouldNotLeakAcrossThreads() throws Exception {
            TenantContext.setCurrentTenant("hcm");

            Thread other = new Thread(() -> {
                assertThat(TenantContext.getCurrentTenant()).isNull();
                TenantContext.setCurrentTenant("hn");
            });
            other.start();
            other.join();

            // Main thread should still have "hcm"
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("hcm");
        }
    }
}
