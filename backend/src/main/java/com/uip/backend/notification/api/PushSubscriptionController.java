package com.uip.backend.notification.api;

import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.notification.api.dto.PushSubscribeRequest;
import com.uip.backend.notification.api.dto.PushSubscriptionResponse;
import com.uip.backend.notification.api.dto.VapidKeyResponse;
import com.uip.backend.notification.config.VapidConfig;
import com.uip.backend.notification.service.AlertNotification;
import com.uip.backend.notification.service.NotificationRouter;
import com.uip.backend.notification.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Push Notifications", description = "Web Push subscription management")
public class PushSubscriptionController {

    private final PushSubscriptionService subscriptionService;
    private final NotificationRouter notificationRouter;
    private final VapidConfig vapidConfig;
    private final AppUserRepository appUserRepository;

    @GetMapping("/vapid-key")
    @Operation(summary = "Get VAPID public key for client-side push subscription")
    public ResponseEntity<VapidKeyResponse> getVapidKey() {
        return ResponseEntity.ok(new VapidKeyResponse(vapidConfig.getPublicKey()));
    }

    @PostMapping("/subscribe")
    @Operation(summary = "Register a push notification subscription")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PushSubscriptionResponse> subscribe(
            @RequestBody @Valid PushSubscribeRequest request,
            Authentication auth) {
        UUID userId = resolveUserId(auth);
        PushSubscriptionResponse response = subscriptionService.subscribe(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/subscriptions/{id}")
    @Operation(summary = "Deactivate a push subscription")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = resolveUserId(auth);
        subscriptionService.unsubscribe(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "List active push subscriptions for the current user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PushSubscriptionResponse>> listSubscriptions(Authentication auth) {
        UUID userId = resolveUserId(auth);
        return ResponseEntity.ok(subscriptionService.listSubscriptions(userId));
    }

    @PostMapping("/test")
    @Operation(summary = "Send a test push notification (non-production only)")
    @Profile("!prod")
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
        log.info("Test push notification triggered by user={}", auth.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Resolve the current user's UUID from the JWT principal (username).
     */
    private UUID resolveUserId(Authentication auth) {
        String username = auth.getName();
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found in database: " + username))
                .getId();
    }
}
