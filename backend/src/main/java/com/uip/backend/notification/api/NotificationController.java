package com.uip.backend.notification.api;

import com.uip.backend.notification.service.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Server-Sent Events for real-time alert notifications")
public class NotificationController {

    private final SseEmitterRegistry sseEmitterRegistry;

    /**
     * SSE endpoint. Clients connect here to receive real-time alert notifications.
     * Event name: "alert", data: AlertEventDto JSON.
     *
     * Client reconnect: use EventSource with retry logic on error.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time alert notifications via SSE")
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream() {
        return sseEmitterRegistry.register();
    }
}
