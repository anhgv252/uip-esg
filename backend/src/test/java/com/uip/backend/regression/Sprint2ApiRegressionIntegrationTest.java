package com.uip.backend.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class Sprint2ApiRegressionIntegrationTest {

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
        registry.add("security.jwt.secret",
                () -> java.util.Base64.getEncoder().encodeToString(
                        "uip-integration-test-secret-32b!".getBytes()));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

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
        try (InputStream is = p.getInputStream()) { is.readAllBytes(); }
        return p.waitFor(timeoutSec, TimeUnit.SECONDS) ? p.exitValue() : -1;
    }

    private static void waitForPostgres(int port, int timeoutSec) throws Exception {
        String url = "jdbc:postgresql://localhost:" + port + "/uip_test";
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try (Connection c = DriverManager.getConnection(url, "uip", "test_password")) {
                return;
            } catch (Exception ignored) {
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("PostgreSQL on port " + port + " did not become ready");
    }

    private static void stopContainer(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        try { runAndWait(15, "/usr/local/bin/docker", "stop", containerId); } catch (Exception ignored) {}
        try { runAndWait(10, "/usr/local/bin/docker", "rm", "-f", containerId); } catch (Exception ignored) {}
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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

    @Test
    @DisplayName("Regression: GET /api/v1/alerts?status=NEW returns 200")
    void alertsQuery_returns200() throws Exception {
        String token = loginAndGetAccessToken();
        mockMvc.perform(get("/api/v1/alerts")
                        .param("status", "NEW")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Regression: GET /api/v1/admin/errors returns 200")
    void adminErrors_returns200() throws Exception {
        String token = loginAndGetAccessToken();
        mockMvc.perform(get("/api/v1/admin/errors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Regression: PUT /api/v1/alerts/{id}/acknowledge with invalid UUID returns 400")
    void acknowledge_invalidUuid_returns400() throws Exception {
        String token = loginAndGetAccessToken();
        mockMvc.perform(put("/api/v1/alerts/not-a-uuid/acknowledge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─── Sprint 1 Auth Regression ────────────────────────────────────────────

    @Nested
    @DisplayName("Sprint 1 — Auth API")
    class AuthRegression {

        @Test
        @DisplayName("POST /auth/login with invalid credentials returns 401")
        void login_invalidCredentials_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"admin","password":"wrong_password"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET protected endpoint without token returns 401")
        void protectedEndpoint_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/alerts"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /auth/logout returns 200 for authenticated user")
        void logout_authenticated_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    // ─── Sprint 1 Environment API Regression ─────────────────────────────────

    @Nested
    @DisplayName("Sprint 1 — Environment API")
    class EnvironmentRegression {

        @Test
        @DisplayName("GET /environment/sensors returns 200 with array")
        void sensors_list_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            MvcResult result = mockMvc.perform(get("/api/v1/environment/sensors")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assert body.isArray() : "Expected array response from /environment/sensors";
        }

        @Test
        @DisplayName("GET /environment/aqi/current returns 200 with array")
        void aqiCurrent_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            MvcResult result = mockMvc.perform(get("/api/v1/environment/aqi/current")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assert body.isArray() : "Expected array response from /environment/aqi/current";
        }

        @Test
        @DisplayName("GET /environment/aqi/history returns 200 with array")
        void aqiHistory_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            MvcResult result = mockMvc.perform(get("/api/v1/environment/aqi/history")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assert body.isArray() : "Expected array response from /environment/aqi/history";
        }
    }

    // ─── Sprint 1 ESG API Regression ─────────────────────────────────────────

    @Nested
    @DisplayName("Sprint 1 — ESG API")
    class EsgRegression {

        @Test
        @DisplayName("GET /esg/summary returns 200")
        void esgSummary_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(get("/api/v1/esg/summary")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /esg/energy returns 200 with array")
        void esgEnergy_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            MvcResult result = mockMvc.perform(get("/api/v1/esg/energy")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assert body.isArray() : "Expected array response from /esg/energy";
        }

        @Test
        @DisplayName("GET /esg/carbon returns 200 with array")
        void esgCarbon_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            MvcResult result = mockMvc.perform(get("/api/v1/esg/carbon")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assert body.isArray() : "Expected array response from /esg/carbon";
        }

        @Test
        @DisplayName("POST /esg/reports/generate with query params returns 200 (ARCH fix: @RequestParam not @RequestBody)")
        void esgGenerateReport_queryParams_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            mockMvc.perform(post("/api/v1/esg/reports/generate")
                            .param("year", "2026")
                            .param("quarter", "1")
                            .param("period", "quarterly")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    // ─── Sprint 2 Alert Regression (extended) ────────────────────────────────

    @Nested
    @DisplayName("Sprint 2 — Alert API (extended)")
    class AlertExtendedRegression {

        @Test
        @DisplayName("GET /alerts?severity=HIGH returns 200 with page structure")
        void alerts_severityFilter_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            MvcResult result = mockMvc.perform(get("/api/v1/alerts")
                            .param("severity", "HIGH")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assert body.has("content") : "Response should have 'content' field (Spring Page)";
            assert body.has("totalElements") : "Response should have 'totalElements' field";
        }

        @Test
        @DisplayName("GET /alerts response items include id as UUID string (V5 arch fix)")
        void alerts_idsAreUuidStrings() throws Exception {
            String token = loginAndGetAccessToken();
            MvcResult result = mockMvc.perform(get("/api/v1/alerts")
                            .param("size", "1")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode content = body.get("content");
            if (content != null && content.isArray() && content.size() > 0) {
                JsonNode first = content.get(0);
                String id = first.get("id").asText();
                // UUIDs have format xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36 chars)
                assert id.length() == 36 && id.contains("-")
                        : "Alert id should be UUID string, got: " + id;
            }
        }

        @Test
        @DisplayName("GET /admin/alert-rules returns 200 with array")
        void alertRules_list_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            MvcResult result = mockMvc.perform(get("/api/v1/admin/alert-rules")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assert body.isArray() : "Expected array response from /admin/alert-rules";
        }

        @Test
        @DisplayName("GET /notifications/stream returns 200 (SSE endpoint alive)")
        void notificationsStream_returns200() throws Exception {
            String token = loginAndGetAccessToken();
            // SSE connection — just check it doesn't 4xx/5xx
            mockMvc.perform(get("/api/v1/notifications/stream")
                            .header("Authorization", "Bearer " + token)
                            .accept("text/event-stream"))
                    .andExpect(status().isOk());
        }
    }
}
