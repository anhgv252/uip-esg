package com.uip.backend.notification.adapter;

import com.uip.backend.common.spi.SseBroadcastPort;
import com.uip.backend.notification.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code notification}-side implementation of {@link SseBroadcastPort} (ADR-052, migration D3 + C3).
 *
 * <p>Delegates to {@link SseEmitterRegistry} so the {@code scheduler} and {@code bms} modules
 * never import {@code notification.service} directly when they only need to push SSE events.</p>
 */
@Component
@RequiredArgsConstructor
public class SseBroadcastAdapter implements SseBroadcastPort {

    private final SseEmitterRegistry sseEmitterRegistry;

    @Override
    public int activeCount() {
        return sseEmitterRegistry.activeCount();
    }

    @Override
    public void broadcast(String eventName, Object payload) {
        sseEmitterRegistry.broadcast(eventName, payload);
    }
}
