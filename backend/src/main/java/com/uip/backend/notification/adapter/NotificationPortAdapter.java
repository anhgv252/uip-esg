package com.uip.backend.notification.adapter;

import com.uip.backend.common.spi.NotificationPort;
import com.uip.backend.notification.service.AlertNotification;
import com.uip.backend.notification.service.NotificationRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code notification}-side implementation of {@link NotificationPort} (ADR-052, migration C2).
 *
 * <p>Wraps {@link NotificationRouter} so the {@code safety} module routes alert notifications
 * via neutral primitives instead of constructing a {@code notification.domain.AlertNotification}
 * and importing {@code notification.service}.</p>
 */
@Component
@RequiredArgsConstructor
public class NotificationPortAdapter implements NotificationPort {

    private final NotificationRouter notificationRouter;

    @Override
    public void routeAlert(String sensorId, String category, String severity, String message, String tenantId) {
        notificationRouter.route(new AlertNotification(
                sensorId,
                category,
                severity,
                message,
                tenantId,
                null
        ));
    }
}
