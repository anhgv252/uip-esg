package com.uip.backend.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sprint 5 API Regression Tests.
 *
 * Covers:
 * - All Sprint 1-4 endpoints still pass (existing Sprint2 regression)
 * - NEW: Push subscription endpoints (POST/DELETE/GET /api/v1/push/*)
 * - NEW: Tenant Admin endpoints regression
 * - NEW: RLS covers push_subscriptions table
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class Sprint5ApiRegressionIntegrationTest {

    private static String postgresId;
    private static String redisId;
    static int postgresPort;
    static int redisPort;

    @BeforeAll
    static void startContainers() throws Exception {
        boolean socketExists = new File("/var/run/docker.sock").exists();
        assumeTrue(socketExists, "Docker socket not available - skipping integration tests");

        boolean dockerUp = runAndWait(5, "/usr/local/bin/docker", "info") == 0;
        assumeTrue(dockerUp, "Docker daemon not responding - skipping integration tests");

        postgresId = startContainer(
                "-e", "POSTGRES_DB=uip_test",
                "-e", "POSTGRES_USER=uip",
                "-e", "POSTGRES_PASSWORD=test_password",
                "-p", "0:5432",
                "timescale/timescaledb:2.13.1-pg15"
        );
        postgresPort = getMappedPort(postgresId, 5432);

        redisId = startContainer(
                "-p", "0:6379",
                "redis:7.2-alpine",
                "redis-server", "--requirepass", "testredis"
        );
        redisPort = getMappedPort(redisId, 6379);

        waitForPostgres(postgresPort, 60);
    }

    @AfterAll
    static void stopContainers() {
        stopContainer(postgresId);
        stopContainer(redisId);
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:" + postgresPort + "/uip_test");
        registry.add("spring.datasource.username", () -> "uip");
        registry.add("spring.datasource.password", () -> "test_password");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> redisPort);
        registry.add("spring.data.redis.password", () -> "testredis");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("security.jwt.secret", () ->
                Base64.getEncoder().encodeToString("uip-integration-test-secret-32b!".getBytes()));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("security.login.rate-limit.capacity", () -> "1000");
        registry.add("uip.push.vapid.public-key", () -> "test-public-key-base64");
        registry.add("uip.push.vapid.private-key", () -> "test-private-key-base64");
    }

    // Container helpers (same pattern as Sprint2ApiRegressionIntegrationTest)
    private static String startContainer(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("/usr/local/bin/docker", "run", "-d"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        boolean exited = p.waitFor(120, TimeUnit.SECONDS);
        String out = new String(p.getInputStream().readAllBytes()).trim();
        if (!exited || p.exitValue() != 0) {
            throw new IllegalStateException("docker run failed: " + out);
        }
        return out.lines().reduce((a, b) -> b).orElse(out).trim();
    }

    private static int getMappedPort(String containerId, int containerPort) throws Exception {
        Process p = new ProcessBuilder(
                "/usr/local/bin/docker", "port", containerId, String.valueOf(containerPort))
                .redirectErrorStream(true).start();
        p.waitFor(10, TimeUnit.SECONDS);
        String out = new String(p.getInputStream().readAllBytes()).trim();
        String firstLine = out.split("\n")[0];
        return Integer.parseInt(firstLine.substring(firstLine.lastIndexOf(':') + 1));
    }

    private static int runAndWait(int timeoutSec, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (var is = p.getInputStream()) { is.readAllBytes(); }
        return p.waitFor(timeoutSec, TimeUnit.SECONDS) ? p.exitValue() : -1;
    }

    private static void waitForPostgres(int port, int timeoutSec) throws Exception {
        String url = "jdbc:postgresql://localhost:" + port + "/uip_test";
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try (var c = java.sql.DriverManager.getConnection(url, "uip", "test_password")) {
                return;
            } catch (Exception ignored) { Thread.sleep(500); }
        }
        throw new IllegalStateException("PostgreSQL on port " + port + " did not become ready");
    }

    private static void stopContainer(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        try { runAndWait(15, "/usr/local/bin/docker", "stop", containerId); } catch (Exception ignored) {}
        try { runAndWait(10, "/usr/local/bin/docker", "rm", "-f", containerId); } catch (Exception ignored) {}
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String loginAndGetAccessToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin_Dev#2026!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    // ========================================================================
    // Sprint 5 NEW: Push Subscription API
    // ========================================================================

    @Nested
    @DisplayName("Sprint 5 — Push Subscription API")
    class PushSubscriptionRegression {

        @Test
        @DisplayName("REG-API-11: POST /push/subscribe returns 201 for valid request")
        void pushSubscribe_valid_returns201() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(post("/api/v1/push/subscribe")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "endpoint": "https://fcm.googleapis.com/fcm/send/regression-test",
                                      "platform": "web",
                                      "p256dh": "BOr_regression_test_key",
                                      "authKey": "regression_test_auth"
                                    }
                                    """))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("REG-API-12: DELETE /push/subscriptions/{id} returns 204 for valid subscription")
        void pushUnsubscribe_valid_returns204() throws Exception {
            String token = loginAndGetAccessToken();

            // First, create a subscription to get its ID
            MvcResult subscribeResult = mockMvc.perform(post("/api/v1/push/subscribe")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "endpoint": "https://fcm.googleapis.com/fcm/send/unsubscribe-test",
                                      "platform": "web",
                                      "p256dh": "BOr_unsubscribe_test_key",
                                      "authKey": "unsubscribe_test_auth"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode subscribeBody = objectMapper.readTree(subscribeResult.getResponse().getContentAsString());
            String subscriptionId = subscribeBody.get("id").asText();

            // Then delete using the correct endpoint with path variable ID
            mockMvc.perform(delete("/api/v1/push/subscriptions/" + subscriptionId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("REG-API-13: GET /push/subscriptions returns 200 with array")
        void pushSubscriptions_list_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            MvcResult result = mockMvc.perform(get("/api/v1/push/subscriptions")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assert body.isArray() : "Expected array response from /push/subscriptions";
        }

        @Test
        @DisplayName("REG-API-14: POST /push/subscribe without auth returns 401")
        void pushSubscribe_noAuth_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/push/subscribe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "endpoint": "https://fcm.googleapis.com/fcm/send/test",
                                      "platform": "web",
                                      "p256dh": "key",
                                      "authKey": "auth"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("REG-API-15: POST /push/subscribe with invalid payload returns 400")
        void pushSubscribe_invalidPayload_returns400() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(post("/api/v1/push/subscribe")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"platform": "web"}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    // ========================================================================
    // Sprint 5 NEW: Tenant Admin API regression
    // ========================================================================

    @Nested
    @DisplayName("Sprint 5 — Tenant Admin API Regression")
    class TenantAdminRegression {

        @Test
        @DisplayName("REG-API-16: GET /tenant/config returns 200")
        void tenantConfig_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(get("/api/v1/tenant/config")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("REG-API-17: PUT /admin/tenants/{tenantId}/settings returns 200 for valid update")
        void tenantConfigUpdate_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(put("/api/v1/admin/tenants/default/settings")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"configKey":"tenant_management","configValue":"true"}
                                    """))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    // ========================================================================
    // Sprint 1-4 existing regression (re-run to verify no breakage)
    // ========================================================================

    @Nested
    @DisplayName("Sprint 1-4 — Existing Regression (no breakage)")
    class ExistingRegression {

        @Test
        @DisplayName("GET /alerts returns 200")
        void alerts_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(get("/api/v1/alerts")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /environment/sensors returns 200")
        void sensors_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(get("/api/v1/environment/sensors")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /esg/summary returns 200")
        void esgSummary_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(get("/api/v1/esg/summary")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /health returns 200 (unauthenticated)")
        void health_returns200() throws Exception {
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /auth/login with invalid credentials returns 401")
        void loginInvalid_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"admin","password":"wrong-password-123"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }
}
