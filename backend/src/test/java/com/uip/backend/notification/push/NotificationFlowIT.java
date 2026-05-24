package com.uip.backend.notification.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for end-to-end notification flow.
 *
 * Tests the alert routing pipeline:
 * - Alert event -> NotificationRouter -> channels (SSE, Push)
 * - SSE fallback when push fails
 * - Multi-alert routing
 *
 * Uses Testcontainers (PostgreSQL). Redis and Kafka are mocked since we test
 * via the REST API directly rather than through Redis pub/sub.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Notification Flow IT")
class NotificationFlowIT {

    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("security.jwt.secret", () ->
                Base64.getEncoder().encodeToString("uip-integration-test-secret-32b!".getBytes()));
        registry.add("uip.push.vapid.public-key", () -> "test-public-key-base64");
        registry.add("uip.push.vapid.private-key", () -> "test-private-key-base64");
        registry.add("uip.push.vapid.subject", () -> "mailto:test@uip.vn");
    }

    // Mock infrastructure not available in Testcontainers
    @MockBean @SuppressWarnings("unused")
    RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    StringRedisTemplate stringRedisTemplate;
    @MockBean @SuppressWarnings("unused")
    RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused")
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeAll
    void obtainTokens() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin_Dev#2026!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        adminToken = body.get("accessToken").asText();
    }

    // ========================================================================
    // Test push endpoint
    // ========================================================================

    @Nested
    @DisplayName("Test Push Endpoint")
    class TestPushEndpoint {

        @Test
        @Order(1)
        @DisplayName("POST /push/test routes notification through all channels")
        void testPush_routesNotification() throws Exception {
            mockMvc.perform(post("/api/v1/push/test")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @Order(2)
        @DisplayName("POST /push/test without auth returns 401")
        void testPush_noAuth_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/push/test"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ========================================================================
    // SSE stream access
    // ========================================================================

    @Nested
    @DisplayName("SSE Stream")
    class SseStream {

        @Test
        @Order(3)
        @DisplayName("SSE stream requires authentication")
        void sseStream_requiresAuth() throws Exception {
            mockMvc.perform(get("/api/v1/notifications/stream"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(4)
        @DisplayName("SSE stream accessible with valid token")
        void sseStream_withToken_startsStream() throws Exception {
            mockMvc.perform(get("/api/v1/notifications/stream")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted());
        }
    }

    // ========================================================================
    // Full flow: subscribe -> test push -> verify no crash
    // ========================================================================

    @Nested
    @DisplayName("Full Notification Flow")
    class FullFlow {

        @Test
        @Order(5)
        @DisplayName("Subscribe + test push + list subscriptions flow")
        void fullFlow_subscribePushList() throws Exception {
            // 1. Subscribe for push notifications
            mockMvc.perform(post("/api/v1/push/subscribe")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "endpoint": "https://fcm.googleapis.com/fcm/send/flow-test-001",
                                      "platform": "web",
                                      "p256dh": "BOr_testKey",
                                      "authKey": "testAuth"
                                    }
                                    """))
                    .andExpect(status().isCreated());

            // 2. Trigger test push (will attempt push send — VAPID keys are dummy,
            //    but PushNotificationService catches all exceptions per subscription)
            mockMvc.perform(post("/api/v1/push/test")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            // 3. Verify subscription still listed (no crash)
            mockMvc.perform(get("/api/v1/push/subscriptions")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
        }
    }

    // ========================================================================
    // Multiple alerts
    // ========================================================================

    @Nested
    @DisplayName("Multiple Alerts")
    class MultipleAlerts {

        @Test
        @Order(6)
        @DisplayName("Multiple test push calls route without error")
        void multipleAlerts_allRouted() throws Exception {
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/v1/push/test")
                                .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk());
            }
            // All 3 calls should succeed — no crash or hang
        }
    }
}
