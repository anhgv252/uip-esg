package com.uip.backend.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SseEmitterRegistry")
class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    @DisplayName("register: returns a non-null SseEmitter and increments count")
    void register_returnsEmitter_incrementsCount() {
        SseEmitter emitter = registry.register();

        assertThat(emitter).isNotNull();
        assertThat(registry.activeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("register: multiple clients all tracked")
    void register_multipleClients_allTracked() {
        registry.register();
        registry.register();
        registry.register();

        assertThat(registry.activeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("activeCount: returns 0 on empty registry")
    void activeCount_empty_returnsZero() {
        assertThat(registry.activeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("broadcast: does not throw when registry is empty")
    void broadcast_emptyRegistry_noException() {
        // Should handle empty registry gracefully
        registry.broadcast("alert", Map.of("key", "value"));

        assertThat(registry.activeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("broadcast: sends to active emitters without error")
    void broadcast_withRegisteredEmitter_sendsEvent() {
        registry.register();
        registry.register();

        // Broadcast to new (open) emitters — should not throw
        registry.broadcast("alert", Map.of("sensorId", "ENV-001", "severity", "CRITICAL"));

        assertThat(registry.activeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("onCompletion: removes emitter from registry when client disconnects")
    void register_onCompletion_removesFromRegistry() throws Exception {
        SseEmitter emitter = registry.register();
        assertThat(registry.activeCount()).isEqualTo(1);

        // SseEmitter.onCompletion stores the Runnable in ResponseBodyEmitter.completionCallback
        // when no live HTTP handler is present (unit test context).
        // Invoke it directly to simulate client disconnect.
        fireCompletionCallback(emitter);

        assertThat(registry.activeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("broadcast with 0 open emitters after all complete")
    void broadcast_afterAllComplete_registryEmpty() throws Exception {
        SseEmitter e1 = registry.register();
        SseEmitter e2 = registry.register();
        fireCompletionCallback(e1);
        fireCompletionCallback(e2);

        assertThat(registry.activeCount()).isEqualTo(0);

        // Safe to broadcast to empty registry
        registry.broadcast("alert", "payload");
    }

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    /**
     * Spring's ResponseBodyEmitter stores the onCompletion Runnable in a private field
     * called "completionCallback" when no live HTTP handler is wired (unit test context).
     * This helper invokes that callback directly to simulate a client disconnect.
     */
    private static void fireCompletionCallback(SseEmitter emitter) throws Exception {
        Field field = emitter.getClass().getSuperclass()
                .getDeclaredField("completionCallback");
        field.setAccessible(true);
        Runnable cb = (Runnable) field.get(emitter);
        if (cb != null) {
            cb.run();
        }
    }
}
