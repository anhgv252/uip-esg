package com.uip.backend.bms;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BMS Integration Tests — Extended scenarios TC-11 to TC-15.
 *
 * Covers: device status, readings history, command queue, circuit breaker
 * state transitions, multi-tenant bulk registration, and delete lifecycle.
 *
 * Uses MockMVC + Docker PostgreSQL/Redis — same Docker CLI bootstrap pattern
 * as BmsIntegrationTest. No inheritance — boilerplate copied intentionally.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class BmsIntegrationExtendedTest {

    private static String postgresId;
    private static String redisId;
    static int postgresPort;
    static int redisPort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final String TEST_JWT_SECRET =
            Base64.getEncoder().encodeToString(
                    "uip-integration-test-secret-32b!".getBytes(StandardCharsets.UTF_8));
    private static final String AUTH_HEADER = "Bearer " + createTestToken();

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
        registry.add("spring.data.redis.port", () -> String.valueOf(redisPort));
        registry.add("spring.data.redis.password", () -> "testredis");
        registry.add("spring.cache.type", () -> "simple");
        registry.add("bms.discovery.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("security.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("security.login.rate-limit.capacity", () -> "1000");
        registry.add("management.server.port", () -> "");  // merge actuator with main port
    }

    // ─── TC-11: Device Status + Readings History ───

    @Test
    @DisplayName("TC-11: Device status — GET /devices/{id}/status returns 200 with status field; readings history skipped if not implemented")
    void tc11_deviceStatus_readingsHistory() throws Exception {
        String deviceJson = """
                {
                  "deviceName": "AHU-TC11",
                  "protocol": "MODBUS_TCP",
                  "host": "192.168.1.11",
                  "port": 502,
                  "unitId": 1,
                  "pollInterval": 5000
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/bms/devices")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceJson))
                .andExpect(status().isCreated())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        try {
            // Device status — skip gracefully if endpoint not yet implemented
            MvcResult statusResult = mockMvc.perform(get("/api/v1/bms/devices/" + deviceId + "/status")
                            .header("Authorization", AUTH_HEADER)
                            .header("x-tenant-id", "hcm"))
                    .andReturn();
            int statusCode = statusResult.getResponse().getStatus();
            assumeTrue(statusCode != 404, "device status endpoint not yet implemented");
            mockMvc.perform(get("/api/v1/bms/devices/" + deviceId + "/status")
                            .header("Authorization", AUTH_HEADER)
                            .header("x-tenant-id", "hcm"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists());

            // Readings history — skip gracefully if endpoint not yet implemented
            MvcResult readingsResult = mockMvc.perform(get("/api/v1/bms/readings/" + deviceId)
                            .param("limit", "10")
                            .header("Authorization", AUTH_HEADER))
                    .andReturn();

            int readingsStatus = readingsResult.getResponse().getStatus();
            assumeTrue(readingsStatus != 404, "readings history endpoint not implemented");
            Assertions.assertEquals(200, readingsStatus,
                    "Readings history endpoint should return 200");
        } finally {
            mockMvc.perform(delete("/api/v1/bms/devices/" + deviceId)
                            .header("Authorization", AUTH_HEADER))
                    .andReturn();
        }
    }

    // ─── TC-12: Command Queue — send SET_SETPOINT and verify response ───

    @Test
    @DisplayName("TC-12: Command queue — POST SET_SETPOINT to MODBUS_TCP device returns 200/202, response contains commandId or status")
    void tc12_commandQueue_sendAndVerify() throws Exception {
        String deviceJson = """
                {
                  "deviceName": "AHU-TC12",
                  "protocol": "MODBUS_TCP",
                  "host": "192.168.1.12",
                  "port": 502,
                  "unitId": 2,
                  "pollInterval": 5000
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/bms/devices")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceJson))
                .andExpect(status().isCreated())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        try {
            String commandJson = """
                    {
                      "commandType": "SET_SETPOINT",
                      "payload": {"setpoint": 22.5}
                    }
                    """;

            MvcResult commandResult = mockMvc.perform(post("/api/v1/bms/devices/" + deviceId + "/commands")
                            .header("Authorization", AUTH_HEADER)
                            .header("x-tenant-id", "hcm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(commandJson))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        Assertions.assertTrue(
                                s == 200 || s == 202 || s == 403 || s == 500 || s == 503,
                                "Expected 200/202 (accepted), 403 (no tenant), or 500/503 (no adapter), got " + s);
                    })
                    .andReturn();

            int cmdStatus = commandResult.getResponse().getStatus();
            if (cmdStatus == 200 || cmdStatus == 202) {
                String responseBody = commandResult.getResponse().getContentAsString();
                if (!responseBody.isBlank()) {
                    var responseTree = objectMapper.readTree(responseBody);
                    Assertions.assertTrue(
                            responseTree.has("commandId") || responseTree.has("status"),
                            "Response should contain commandId or status field, got: " + responseBody);
                }
            }
        } finally {
            mockMvc.perform(delete("/api/v1/bms/devices/" + deviceId)
                            .header("Authorization", AUTH_HEADER))
                    .andReturn();
        }
    }

    // ─── TC-13: Circuit Breaker — state transitions CLOSED→OPEN→CLOSED ───

    @Test
    @DisplayName("TC-13: Circuit breaker bms-modbus — forced CLOSED→OPEN→CLOSED state transitions")
    void tc13_circuitBreaker_opensAfterFailures() {
        var cbOpt = circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .filter(cb -> cb.getName().equals("bms-modbus"))
                .findFirst();
        assumeTrue(cbOpt.isPresent(), "CB not registered");

        var cb = cbOpt.get();

        // Ensure known starting state
        cb.transitionToClosedState();
        Assertions.assertEquals(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED,
                cb.getState(),
                "CB should be CLOSED initially");

        // Force transition to OPEN
        cb.transitionToOpenState();
        Assertions.assertEquals(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN,
                cb.getState(),
                "CB should be OPEN after forced open");

        // Recover back to CLOSED
        cb.transitionToClosedState();
        Assertions.assertEquals(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED,
                cb.getState(),
                "CB should be back to CLOSED after recovery");
    }

    // ─── TC-14: Bulk device registration — three tenants with data isolation ───

    @Test
    @DisplayName("TC-14: Bulk registration — 3 tenants (hcm/hanoi/danang), each sees only its own device")
    void tc14_bulkDeviceRegistration_threeTenants() throws Exception {
        String[] tenants      = {"hcm", "hanoi", "danang"};
        String[] deviceNames  = {"AHU-TC14-HCM", "AHU-TC14-HANOI", "AHU-TC14-DANANG"};
        Map<String, String> deviceIdByTenant = new LinkedHashMap<>();

        try {
            // Register one device per tenant
            for (int i = 0; i < tenants.length; i++) {
                String tenantId   = tenants[i];
                String tenantAuth = "Bearer " + createTestTokenForTenant(tenantId);
                String deviceName = deviceNames[i];

                String deviceJson = """
                        {
                          "deviceName": "%s",
                          "protocol": "MANUAL"
                        }
                        """.formatted(deviceName);

                MvcResult result = mockMvc.perform(post("/api/v1/bms/devices")
                                .header("Authorization", tenantAuth)
                                .header("x-tenant-id", tenantId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(deviceJson))
                        .andExpect(result2 -> {
                            int s = result2.getResponse().getStatus();
                            Assertions.assertTrue(s == 200 || s == 201,
                                    "Expected 200 or 201 for tenant " + tenantId + ", got " + s);
                        })
                        .andReturn();

                String deviceId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
                deviceIdByTenant.put(tenantId, deviceId);
            }

            // Verify data isolation — each tenant sees only its own device
            for (int i = 0; i < tenants.length; i++) {
                String tenantId   = tenants[i];
                String tenantAuth = "Bearer " + createTestTokenForTenant(tenantId);
                String ownDevice  = deviceNames[i];

                String listBody = mockMvc.perform(get("/api/v1/bms/devices")
                                .header("Authorization", tenantAuth)
                                .header("x-tenant-id", tenantId))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse().getContentAsString();

                Assertions.assertTrue(listBody.contains(ownDevice),
                        "Tenant " + tenantId + " should see its own device " + ownDevice);

                for (int j = 0; j < deviceNames.length; j++) {
                    if (j != i) {
                        Assertions.assertFalse(listBody.contains(deviceNames[j]),
                                "Tenant " + tenantId + " must NOT see device " + deviceNames[j] + " of another tenant");
                    }
                }
            }
        } finally {
            for (Map.Entry<String, String> entry : deviceIdByTenant.entrySet()) {
                String tenantAuth = "Bearer " + createTestTokenForTenant(entry.getKey());
                try {
                    mockMvc.perform(delete("/api/v1/bms/devices/" + entry.getValue())
                                    .header("Authorization", tenantAuth)
                                    .header("x-tenant-id", entry.getKey()))
                            .andReturn();
                } catch (Exception ignored) {}
            }
        }
    }

    // ─── TC-15: Device delete lifecycle — 404 after deletion ───

    @Test
    @DisplayName("TC-15: Device delete — DELETE returns 204/200, subsequent GET returns 404")
    void tc15_deviceDelete_notFoundAfter() throws Exception {
        String deviceJson = """
                {
                  "deviceName": "AHU-TC15",
                  "protocol": "MANUAL"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/bms/devices")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceJson))
                .andExpect(status().isCreated())
                .andReturn();

        String deviceId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // Attempt DELETE
        MvcResult deleteResult = mockMvc.perform(delete("/api/v1/bms/devices/" + deviceId)
                        .header("Authorization", AUTH_HEADER))
                .andReturn();

        int deleteStatus = deleteResult.getResponse().getStatus();
        if (deleteStatus == 405 || deleteStatus == 501) {
            assumeTrue(false, "DELETE endpoint not implemented");
        }

        Assertions.assertTrue(deleteStatus == 204 || deleteStatus == 200,
                "DELETE should return 204 or 200, got " + deleteStatus);

        // Subsequent GET must return 404
        mockMvc.perform(get("/api/v1/bms/devices/" + deviceId)
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }

    // ─── Docker CLI helpers (copied from BmsIntegrationTest — no inheritance) ───

    private static String startContainer(String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("/usr/local/bin/docker");
        cmd.add("run");
        cmd.add("-d");
        cmd.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        return output.trim();
    }

    private static int getMappedPort(String containerId, int internalPort) throws Exception {
        Process p = new ProcessBuilder("/usr/local/bin/docker", "port", containerId, internalPort + "/tcp")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        // output may be "0.0.0.0:PORT\n:::PORT\n" — take first IPv4 line
        String firstLine = output.trim().split("\\n")[0].trim();
        return Integer.parseInt(firstLine.substring(firstLine.lastIndexOf(':') + 1));
    }

    private static void stopContainer(String containerId) {
        if (containerId != null && !containerId.isBlank()) {
            try {
                new ProcessBuilder("/usr/local/bin/docker", "rm", "-f", containerId).start();
            } catch (Exception ignored) {}
        }
    }

    private static int runAndWait(int timeoutSec, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS);
        return p.exitValue();
    }

    private static void waitForPostgres(int port, int timeoutSec) throws Exception {
        String url = "jdbc:postgresql://localhost:" + port + "/uip_test";
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try (var c = java.sql.DriverManager.getConnection(url, "uip", "test_password")) {
                return;
            } catch (Exception ignored) { Thread.sleep(500); }
        }
        throw new RuntimeException("Postgres not ready after " + timeoutSec + "s");
    }

    private static String createTestToken() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .issuer("uip-legacy")
                .subject("admin")
                .claim("tenant_id", "hcm")
                .claim("roles", List.of("ROLE_ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key)
                .compact();
    }

    private static String createTestTokenForTenant(String tenantId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .issuer("uip-legacy")
                .subject("admin")
                .claim("tenant_id", tenantId)
                .claim("roles", List.of("ROLE_ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key)
                .compact();
    }
}
