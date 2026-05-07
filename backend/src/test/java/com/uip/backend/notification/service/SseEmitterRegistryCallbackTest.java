package com.uip.backend.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Additional tests covering the timeout/error callbacks and IOException broadcast path
 * in SseEmitterRegistry.
 */
class SseEmitterRegistryCallbackTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    void onTimeout_removesEmitter() throws Exception {
        SseEmitter emitter = registry.register();
        assertThat(registry.activeCount()).isEqualTo(1);

        // Fire the timeout callback
        fireTimeoutCallback(emitter);

        assertThat(registry.activeCount()).isEqualTo(0);
    }

    @Test
    void onError_removesEmitter() throws Exception {
        SseEmitter emitter = registry.register();
        assertThat(registry.activeCount()).isEqualTo(1);

        // Fire the error callback with a dummy exception
        fireErrorCallback(emitter, new RuntimeException("connection reset"));

        assertThat(registry.activeCount()).isEqualTo(0);
    }

    @Test
    void broadcast_completedEmitter_doesNotPropagateException() throws Exception {
        // Register 2 emitters, complete one, broadcast to all → no exception thrown
        SseEmitter active = registry.register();
        SseEmitter completed = registry.register();
        assertThat(registry.activeCount()).isEqualTo(2);

        // Complete the second one so next send throws an exception
        completed.complete();

        // broadcast should handle the error internally without throwing
        assertThatCode(() -> registry.broadcast("test", "payload"))
                .doesNotThrowAnyException();
    }

    private static void fireTimeoutCallback(SseEmitter emitter) throws Exception {
        Field field = emitter.getClass().getSuperclass().getDeclaredField("timeoutCallback");
        field.setAccessible(true);
        Runnable cb = (Runnable) field.get(emitter);
        if (cb != null) cb.run();
    }

    private static void fireErrorCallback(SseEmitter emitter, Throwable error) throws Exception {
        Field field = emitter.getClass().getSuperclass().getDeclaredField("errorCallback");
        field.setAccessible(true);
        // Spring uses SseEmitter.SseEventBuilder interface; errorCallback is Consumer<Throwable>
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<Throwable> cb = (java.util.function.Consumer<Throwable>) field.get(emitter);
        if (cb != null) cb.accept(error);
    }
}
