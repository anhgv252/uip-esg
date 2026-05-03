package com.uip.backend.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTaskDecoratorTest {

    private final TenantContextTaskDecorator decorator = new TenantContextTaskDecorator();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("should propagate tenant to child thread")
    void shouldPropagateTenant() throws Exception {
        TenantContext.setCurrentTenant("hcm");

        AtomicReference<String> captured = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> captured.set(TenantContext.getCurrentTenant()));

        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        assertThat(captured.get()).isEqualTo("hcm");
    }

    @Test
    @DisplayName("should clear tenant context after execution")
    void shouldClearAfterExecution() throws Exception {
        TenantContext.setCurrentTenant("hcm");

        AtomicReference<String> afterRun = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            // Do nothing; we check after run
        });

        Thread thread = new Thread(() -> {
            decorated.run();
            afterRun.set(TenantContext.getCurrentTenant());
        });
        thread.start();
        thread.join();

        assertThat(afterRun.get()).isNull();
    }

    @Test
    @DisplayName("should handle null parent context gracefully")
    void shouldHandleNullParentContext() throws Exception {
        // Parent context not set
        TenantContext.clear();

        AtomicReference<String> captured = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> captured.set(TenantContext.getCurrentTenant()));

        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        // null tenant set → setCurrentTenant(null) → fallback "default"
        assertThat(captured.get()).isEqualTo("default");
    }

    @Test
    @DisplayName("should not affect parent thread context")
    void shouldNotAffectParent() throws Exception {
        TenantContext.setCurrentTenant("hcm");

        Runnable decorated = decorator.decorate(() -> {
            TenantContext.setCurrentTenant("other");
        });

        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        assertThat(TenantContext.getCurrentTenant()).isEqualTo("hcm");
    }
}
