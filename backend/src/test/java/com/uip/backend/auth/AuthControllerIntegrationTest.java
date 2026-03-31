package com.uip.backend.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    // Use Docker CLI directly — bypasses docker-java which fails on Docker Engine 29.x
    // (docker-java 3.3.x uses deprecated API v1.24 which Docker 26+ rejects with HTTP 400).
    private static String postgresId;
    private static String redisId;
    static int postgresPort;
    static int redisPort;

    @BeforeAll
    static void startContainers() throws Exception {
        boolean socketExists = new File("/var/run/docker.sock").exists();
        assumeTrue(socketExists, "Docker socket not available — skipping integration tests");

        // Verify Docker CLI is reachable
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

        // Wait for PostgreSQL to accept connections
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
        List<String> cmd = new ArrayList<>(List.of("/usr/local/bin/docker", "run", "-d")); // no --rm: prevents auto-removal before port query
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        boolean exited = p.waitFor(120, TimeUnit.SECONDS);
        String out = new String(p.getInputStream().readAllBytes()).trim();
        if (!exited || p.exitValue() != 0) {
            throw new IllegalStateException("docker run failed: " + out);
        }
        // docker run -d outputs the full 64-char container ID as the last line
        // (earlier lines may contain image-pull progress when stderr is merged)
        return out.lines().reduce((a, b) -> b).orElse(out).trim();
    }

    private static int getMappedPort(String containerId, int containerPort) throws Exception {
        Process p = new ProcessBuilder(
                "/usr/local/bin/docker", "port", containerId, String.valueOf(containerPort))
                .redirectErrorStream(true).start();
        p.waitFor(10, TimeUnit.SECONDS);
        String out = new String(p.getInputStream().readAllBytes()).trim();
        // output: "0.0.0.0:XXXXX\n..." — take first line, last segment after ':'
        String firstLine = out.split("\n")[0];
        return Integer.parseInt(firstLine.substring(firstLine.lastIndexOf(':') + 1));
    }

    private static int runAndWait(int timeoutSec, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        // drain output to avoid blocking
        try (InputStream is = p.getInputStream()) { is.readAllBytes(); }
        return p.waitFor(timeoutSec, TimeUnit.SECONDS) ? p.exitValue() : -1;
    }

    private static void waitForPostgres(int port, int timeoutSec) throws Exception {
        String url = "jdbc:postgresql://localhost:" + port + "/uip_test";
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try (Connection c = DriverManager.getConnection(url, "uip", "test_password")) {
                return; // connected
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

    @Test
    @DisplayName("GET /api/v1/health returns 200 without auth")
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with valid credentials returns tokens")
    void login_validCredentials_returnsTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin_Dev#2026!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrongpassword1!"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/environment/sensors without token returns 401")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/environment/sensors"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with blank username returns 400")
    void login_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"somepassword"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
