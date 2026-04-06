package com.uip.backend.openapi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Generates docs/api/openapi.json from the running Spring context.
 *
 * Run manually with:
 *   ./gradlew test --tests "*.OpenApiSpecGeneratorTest" -Dgenerate.openapi=true
 *
 * The generated file is the single source of truth for frontend TypeScript types.
 * After running this test, execute:
 *   cd frontend && npm run generate:types
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Tag("openapi-gen")
class OpenApiSpecGeneratorTest {

    private static final boolean DOCKER_EXISTS = new File("/usr/local/bin/docker").exists()
            || new File("/usr/bin/docker").exists();

    private static String postgresId;
    private static String redisId;

    @BeforeAll
    static void startContainers() throws Exception {
        assumeTrue(DOCKER_EXISTS, "Docker not available — skipping spec generation");

        postgresId = startContainer(
                "--name", "spec-pg-" + System.currentTimeMillis(),
                "-e", "POSTGRES_DB=uip_test",
                "-e", "POSTGRES_USER=uip",
                "-e", "POSTGRES_PASSWORD=test_password",
                "-p", "0:5432",
                "timescale/timescaledb:latest-pg16");

        redisId = startContainer(
                "--name", "spec-redis-" + System.currentTimeMillis(),
                "-e", "REDIS_PASSWORD=testredis",
                "-p", "0:6379",
                "redis:7-alpine",
                "redis-server", "--requirepass", "testredis");

        int pgPort = getMappedPort(postgresId, 5432);
        waitForPostgres(pgPort, 60);
    }

    @org.junit.jupiter.api.AfterAll
    static void stopContainers() {
        stopContainer(postgresId);
        stopContainer(redisId);
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) throws Exception {
        if (!DOCKER_EXISTS) return;
        int pgPort = getMappedPort(postgresId, 5432);
        int redisPort = getMappedPort(redisId, 6379);
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:" + pgPort + "/uip_test");
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

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("Generate OpenAPI spec → docs/api/openapi.json")
    void generateOpenApiSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        // Write spec to workspace-relative path
        Path specPath = Paths.get(System.getProperty("user.dir"))
                .getParent()
                .resolve("docs/api/openapi.json");
        Files.createDirectories(specPath.getParent());
        Files.writeString(specPath, json);

        System.out.println("[OpenApiSpecGeneratorTest] Spec written to: " + specPath);
        System.out.println("[OpenApiSpecGeneratorTest] Size: " + json.length() + " bytes");
    }

    // ─── Container helpers ────────────────────────────────────────────────────

    private static String startContainer(String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("/usr/local/bin/docker", "run", "-d"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        boolean exited = p.waitFor(120, TimeUnit.SECONDS);
        String out = new String(p.getInputStream().readAllBytes()).trim();
        if (!exited || p.exitValue() != 0) throw new IllegalStateException("docker run failed: " + out);
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
            } catch (Exception ignored) { Thread.sleep(500); }
        }
        throw new IllegalStateException("PostgreSQL on port " + port + " did not become ready");
    }

    private static void stopContainer(String containerId) {
        if (containerId == null || containerId.isBlank()) return;
        try { runAndWait(15, "/usr/local/bin/docker", "stop", containerId); } catch (Exception ignored) {}
        try { runAndWait(10, "/usr/local/bin/docker", "rm", "-f", containerId); } catch (Exception ignored) {}
    }
}
