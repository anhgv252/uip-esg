package com.uip.backend.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.common.exception.GlobalExceptionHandler;
import com.uip.backend.notification.api.PushSubscriptionController;
import com.uip.backend.notification.api.dto.PushSubscriptionResponse;
import com.uip.backend.notification.config.VapidConfig;
import com.uip.backend.notification.service.NotificationRouter;
import com.uip.backend.notification.service.PushSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PushSubscriptionController.
 *
 * Sprint 5 push notification backend spike.
 * Covers: subscribe, unsubscribe, list subscriptions, validation, error mapping.
 *
 * Uses standalone MockMvc with .principal() to inject Authentication.
 * This avoids Spring Security filter chain issues in @WebMvcTest.
 *
 * Full auth/RBAC tests are in PushSubscriptionIT with @SpringBootTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PushSubscriptionController")
class PushSubscriptionControllerTest {

    @Mock private PushSubscriptionService pushSubscriptionService;
    @Mock private NotificationRouter notificationRouter;
    @Mock private VapidConfig vapidConfig;
    @Mock private AppUserRepository appUserRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    private static final String BASE_URL = "/api/v1/push";
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final String TEST_USERNAME = "testuser";

    /** Authentication provided via .principal() on each secured request. */
    private final Authentication testAuth = new TestingAuthenticationToken(TEST_USERNAME, "test");

    @BeforeEach
    void setUp() {
        AppUser testUser = new AppUser();
        testUser.setId(TEST_USER_ID);
        testUser.setUsername(TEST_USERNAME);
        lenient().when(appUserRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));

        PushSubscriptionController controller = new PushSubscriptionController(
                pushSubscriptionService, notificationRouter, vapidConfig, appUserRepository);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ========================================================================
    // GET /api/v1/push/vapid-key
    // ========================================================================

    @Nested
    @DisplayName("GET /vapid-key")
    class VapidKey {

        @Test
        @DisplayName("Returns VAPID public key as JSON (no auth required)")
        void returnsVapidPublicKey() throws Exception {
            when(vapidConfig.getPublicKey()).thenReturn("BP3xTestPublicKeyBase64EncodedValue");

            mockMvc.perform(get(BASE_URL + "/vapid-key"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicKey").value("BP3xTestPublicKeyBase64EncodedValue"));
        }
    }

    // ========================================================================
    // POST /api/v1/push/subscribe
    // ========================================================================

    @Nested
    @DisplayName("POST /subscribe")
    class Subscribe {

        @Test
        @DisplayName("Valid subscription request returns 201 Created")
        void validSubscription_returns201() throws Exception {
            PushSubscriptionResponse response = new PushSubscriptionResponse(
                    UUID.randomUUID(), "web", "https://fcm.googleapis.com/fcm/send/abc123",
                    true, Instant.now());
            when(pushSubscriptionService.subscribe(eq(TEST_USER_ID), any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL + "/subscribe")
                            .principal(testAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "endpoint": "https://fcm.googleapis.com/fcm/send/abc123",
                                      "platform": "web",
                                      "p256dh": "BOr_testPublicKeyThatIsAtLeast64Chars",
                                      "authKey": "testAuthKeyBase64"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.platform").value("web"))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.endpoint").value("https://fcm.googleapis.com/fcm/send/abc123"));
        }

        @Test
        @DisplayName("Missing endpoint returns 400 Bad Request")
        void missingEndpoint_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL + "/subscribe")
                            .principal(testAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "platform": "web",
                                      "p256dh": "key",
                                      "authKey": "auth"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing platform returns 400 Bad Request")
        void missingPlatform_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL + "/subscribe")
                            .principal(testAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "endpoint": "https://fcm.googleapis.com/fcm/send/abc123",
                                      "p256dh": "key",
                                      "authKey": "auth"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Max subscriptions limit returns 503 Service Unavailable")
        void maxSubscriptions_returns503() throws Exception {
            when(pushSubscriptionService.subscribe(eq(TEST_USER_ID), any()))
                    .thenThrow(new IllegalStateException("Maximum push subscriptions reached (10) for user " + TEST_USER_ID));

            mockMvc.perform(post(BASE_URL + "/subscribe")
                            .principal(testAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "endpoint": "https://fcm.googleapis.com/fcm/send/abc123",
                                      "platform": "web",
                                      "p256dh": "key",
                                      "authKey": "auth"
                                    }
                                    """))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("Duplicate endpoint triggers upsert (returns 201)")
        void duplicateEndpoint_upserts() throws Exception {
            UUID existingId = UUID.randomUUID();
            PushSubscriptionResponse response = new PushSubscriptionResponse(
                    existingId, "web", "https://fcm.googleapis.com/fcm/send/existing",
                    true, Instant.now());
            when(pushSubscriptionService.subscribe(eq(TEST_USER_ID), any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL + "/subscribe")
                            .principal(testAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "endpoint": "https://fcm.googleapis.com/fcm/send/existing",
                                      "platform": "web",
                                      "p256dh": "newKey",
                                      "authKey": "newAuth"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(existingId.toString()));
        }

        @Test
        @DisplayName("Empty body returns 400 Bad Request")
        void emptyBody_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL + "/subscribe")
                            .principal(testAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========================================================================
    // DELETE /api/v1/push/subscriptions/{id}
    // ========================================================================

    @Nested
    @DisplayName("DELETE /subscriptions/{id}")
    class Unsubscribe {

        @Test
        @DisplayName("Valid unsubscribe returns 204 No Content")
        void validUnsubscribe_returns204() throws Exception {
            UUID subscriptionId = UUID.randomUUID();
            doNothing().when(pushSubscriptionService).unsubscribe(subscriptionId, TEST_USER_ID);

            mockMvc.perform(delete(BASE_URL + "/subscriptions/{id}", subscriptionId)
                            .principal(testAuth))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Unsubscribe non-existent subscription returns 400 (IllegalArgumentException)")
        void notFound_returns400() throws Exception {
            UUID subscriptionId = UUID.randomUUID();
            doThrow(new IllegalArgumentException("Subscription not found: " + subscriptionId))
                    .when(pushSubscriptionService).unsubscribe(subscriptionId, TEST_USER_ID);

            mockMvc.perform(delete(BASE_URL + "/subscriptions/{id}", subscriptionId)
                            .principal(testAuth))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Unsubscribe not owned by user returns 500 (SecurityException)")
        void notOwner_returns500() throws Exception {
            UUID subscriptionId = UUID.randomUUID();
            doThrow(new SecurityException("User " + TEST_USER_ID + " cannot unsubscribe subscription owned by " + UUID.randomUUID()))
                    .when(pushSubscriptionService).unsubscribe(subscriptionId, TEST_USER_ID);

            mockMvc.perform(delete(BASE_URL + "/subscriptions/{id}", subscriptionId)
                            .principal(testAuth))
                    .andExpect(status().isInternalServerError());
        }
    }

    // ========================================================================
    // GET /api/v1/push/subscriptions
    // ========================================================================

    @Nested
    @DisplayName("GET /subscriptions")
    class ListSubscriptions {

        @Test
        @DisplayName("Returns user's active subscriptions")
        void returnsUserSubscriptions() throws Exception {
            PushSubscriptionResponse sub1 = new PushSubscriptionResponse(
                    UUID.randomUUID(), "web", "https://fcm.google.com/ep1", true, Instant.now());
            PushSubscriptionResponse sub2 = new PushSubscriptionResponse(
                    UUID.randomUUID(), "android", "https://fcm.google.com/ep2", true, Instant.now());
            when(pushSubscriptionService.listSubscriptions(TEST_USER_ID))
                    .thenReturn(List.of(sub1, sub2));

            mockMvc.perform(get(BASE_URL + "/subscriptions")
                            .principal(testAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].platform").value("web"))
                    .andExpect(jsonPath("$[1].platform").value("android"));
        }

        @Test
        @DisplayName("Returns empty array when no subscriptions")
        void noSubscriptions_returnsEmptyArray() throws Exception {
            when(pushSubscriptionService.listSubscriptions(TEST_USER_ID))
                    .thenReturn(List.of());

            mockMvc.perform(get(BASE_URL + "/subscriptions")
                            .principal(testAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    // ========================================================================
    // POST /api/v1/push/test (dev only)
    // ========================================================================

    @Nested
    @DisplayName("POST /test")
    class TestPush {

        @Test
        @DisplayName("Test push returns 200 and routes notification")
        void testPush_sendsNotification() throws Exception {
            doNothing().when(notificationRouter).route(any());

            mockMvc.perform(post(BASE_URL + "/test")
                            .principal(testAuth))
                    .andExpect(status().isOk());

            verify(notificationRouter).route(any());
        }

        @Test
        @DisplayName("Test push notification contains expected fields")
        void testPush_correctPayload() throws Exception {
            doNothing().when(notificationRouter).route(any());

            mockMvc.perform(post(BASE_URL + "/test")
                            .principal(testAuth))
                    .andExpect(status().isOk());

            verify(notificationRouter).route(argThat(notification ->
                    "test-sensor-001".equals(notification.sensorId()) &&
                    "test".equals(notification.module()) &&
                    "INFO".equals(notification.severity()) &&
                    notification.message() != null));
        }
    }

    // ========================================================================
    // Auth resolution
    // ========================================================================

    @Nested
    @DisplayName("Auth Resolution")
    class AuthResolution {

        @Test
        @DisplayName("Authenticated user with unknown username returns 500 (IllegalStateException)")
        void unknownUser_returnsError() throws Exception {
            when(appUserRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

            mockMvc.perform(post(BASE_URL + "/subscribe")
                            .principal(testAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "endpoint": "https://fcm.google.com/ep",
                                      "platform": "web"
                                    }
                                    """))
                    .andExpect(status().is5xxServerError());
        }
    }
}
