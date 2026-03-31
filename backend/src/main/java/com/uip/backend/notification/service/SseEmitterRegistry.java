package com.uip.backend.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of active SSE emitters.
 * Each connected client gets a unique UUID key.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register() {
        String clientId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L); // no timeout

        emitters.put(clientId, emitter);

        emitter.onCompletion(() -> {
            emitters.remove(clientId);
            log.debug("SSE client disconnected: {}", clientId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(clientId);
            emitter.complete();
        });
        emitter.onError(e -> {
            emitters.remove(clientId);
            log.debug("SSE error for client {}: {}", clientId, e.getMessage());
        });

        log.debug("SSE client registered: {}; active={}", clientId, emitters.size());
        return emitter;
    }

    /**
     * Broadcast a named event with data to all connected clients.
     */
    public void broadcast(String eventName, Object data) {
        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                log.debug("SSE send failed for client {}; removing", clientId);
                emitters.remove(clientId);
                emitter.completeWithError(e);
            }
        });
    }

    public int activeCount() {
        return emitters.size();
    }
}
