package com.uip.backend.common.spi;

/**
 * Port for broadcasting Server-Sent Events (SSE) payloads to connected clients.
 *
 * <p>Shared infrastructure used by multiple modules ({@code scheduler} for sensor updates,
 * {@code bms} for command ack). Consumers must not depend on {@code notification.service}
 * directly (ADR-052, migration D3 + C3). The {@code notification} module provides the
 * implementation backed by its {@code SseEmitterRegistry}.</p>
 */
public interface SseBroadcastPort {

    /**
     * @return number of currently connected SSE clients.
     */
    int activeCount();

    /**
     * Broadcast a named event with the given payload to all connected clients.
     */
    void broadcast(String eventName, Object payload);
}
