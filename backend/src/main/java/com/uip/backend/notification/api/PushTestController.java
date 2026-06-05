package com.uip.backend.notification.api;

import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.notification.service.AlertNotification;
import com.uip.backend.notification.service.NotificationRouter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Non-production test endpoint for triggering a push notification delivery end-to-end.
 * Excluded from production via @Profile("!production") — SA-028 fix.
 */
@RestController
@RequestMapping("/api/v1/push")
@Profile("!production")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Push Notifications", description = "Web Push subscription management")
public class PushTestController {

    private final NotificationRouter notificationRouter;
    private final AppUserRepository appUserRepository;

    @PostMapping("/test")
    @Operation(summary = "Send a test push notification (non-production only)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> testPush(Authentication auth) {
        UUID userId = resolveUserId(auth);
        AlertNotification testNotification = new AlertNotification(
                "test-sensor-001",
                "test",
                "INFO",
                "Test push notification from UIP platform",
                "default",
                null
        );
        notificationRouter.route(testNotification);
        log.info("Test push notification triggered by user={} (userId={})", auth.getName(), userId);
        return ResponseEntity.ok().build();
    }

    private UUID resolveUserId(Authentication auth) {
        String username = auth.getName();
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found in database: " + username))
                .getId();
    }
}
