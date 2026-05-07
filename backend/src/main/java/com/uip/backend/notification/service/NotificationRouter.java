package com.uip.backend.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Dispatches AlertNotification to all registered NotificationChannel implementations.
 * Each channel is invoked in its own try-catch so a failure in one channel
 * (e.g. Web Push timeout) does not block others (e.g. SSE).
 */
@Service
@Slf4j
public class NotificationRouter {

    private final List<NotificationChannel> channels;

    public NotificationRouter(List<NotificationChannel> channels) {
        this.channels = channels;
        log.info("NotificationRouter initialized with {} channels: {}",
                channels.size(),
                channels.stream().map(NotificationChannel::getChannelName).toList());
    }

    /**
     * Route an alert notification to every registered channel.
     * Failures are logged per channel but never propagate to the caller.
     */
    public void route(AlertNotification notification) {
        for (NotificationChannel channel : channels) {
            try {
                channel.send(notification);
            } catch (Exception e) {
                log.error("Failed to send notification via channel '{}' for sensor={} tenant={}: {}",
                        channel.getChannelName(),
                        notification.sensorId(),
                        notification.tenantId(),
                        e.getMessage(),
                        e);
            }
        }
    }
}
