package com.uip.backend.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("SSE Notification Security")
class NotificationControllerIntegrationTest {

    private static String postgresId;
    private static String redisId;
    static int postgresPort;
    static int redisPort;

    @BeforeAll
    static void startContainers() throws Exception {
        boolean socketExists = new File("/var/run/docker.sock").exists();
        assumeTrue(socketExists, "Docker socket not available — skipping integration tests");

        boolean dockerUp = runAndWait(5, "/usr/local/bin/docker", "info") == 0;
        assumeTrue(dockerUp, "Docker daemon not responding — skipping integration tests");

        // Start TimescaleDB
        postgresId = startContainer(
            "-e", "POSTGRES_DB=uip_test",
            "-e", "POSTGRES_USER=uip",
            "-e", "POSTGRES_PASSWORD=test_password",
            "-p", "0:5432",
            "timescale/timescaledb:2.13.1-pg15"
        );
        postgresPort = getMappedPort(postgresId, 5432);

        // Start Redis
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

    // ── Docker CLI helpers ───────────────────────────────────────────────────

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
        throw new IllegalStateException("PostgreSQL on port " + port + " did not become ready in " + timeoutSec + "s");
    }

    private static void stopContainer(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        try { runAndWait(15, "/usr/local/bin/docker", "stop", containerId); } catch (Exception ignored) {}
        try { runAndWait(10, "/usr/local/bin/docker", "rm", "-f", containerId); } catch (Exception ignored) {}
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/notifications/stream without auth returns 401")
    void sseStream_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/stream with Bearer token returns SSE stream")
    void sseStream_withBearerToken_returnsStream() throws Exception {
        // 1. Login to get access token
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin_Dev#2026!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        String accessToken = json.get("accessToken").asText();

        // 2. Connect to SSE with Authorization header
        mockMvc.perform(get("/api/v1/notifications/stream")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/stream with access_token cookie returns SSE stream")
    void sseStream_withCookie_returnsStream() throws Exception {
        // 1. Login to get access token (also sets httpOnly cookie)
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin_Dev#2026!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        String accessToken = json.get("accessToken").asText();

        // 2. Connect to SSE using the cookie
        Cookie accessTokenCookie = new Cookie("access_token", accessToken);
        mockMvc.perform(get("/api/v1/notifications/stream")
                .cookie(accessTokenCookie))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/stream with token in URL query param is rejected")
    void sseStream_withQueryParamToken_returns401() throws Exception {
        // 1. Login to get access token
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin_Dev#2026!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        String accessToken = json.get("accessToken").asText();

        // 2. Try to connect with token in query param (INSECURE - should be rejected)
        mockMvc.perform(get("/api/v1/notifications/stream?token=" + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login sets access_token cookie with correct attributes")
    void login_setsHttpOnlyCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin_Dev#2026!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().path("access_token", "/"))
                .andReturn();

        // Verify cookie value matches token in response body
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        String accessToken = json.get("accessToken").asText();

        Cookie cookie = result.getResponse().getCookie("access_token");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(accessToken);
    }
}
