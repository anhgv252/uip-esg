package com.uip.backend.workflow.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MVP2-03b: Integration test for TriggerConfigCacheService with real Spring Cache + Redis.
 * Uses Docker CLI for containers (same pattern as AuthControllerIntegrationTest).
 *
 * Tests @Cacheable and @CacheEvict behaviour through the actual Spring proxy,
 * which the unit test (TriggerConfigCacheServiceTest) cannot verify.
 */
@SpringBootTest
@DisplayName("MVP2-03b TriggerConfigCacheService — Spring Cache IT")
class TriggerConfigCacheServiceIT {

    private static String postgresId;
    private static String redisId;
    private static int postgresPort;
    private static int redisPort;

    @Autowired
    private TriggerConfigCacheService cacheService;

    @Autowired
    private TriggerConfigRepository configRepo;

    @Autowired
    private CacheManager cacheManager;

    @BeforeAll
    static void startContainers() throws Exception {
        boolean socketExists = new File("/var/run/docker.sock").exists();
        assumeTrue(socketExists, "Docker socket not available -- skipping integration tests");

        boolean dockerUp = runAndWait(5, "/usr/local/bin/docker", "info") == 0;
        assumeTrue(dockerUp, "Docker daemon not responding -- skipping integration tests");

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

    @BeforeEach
    void clearCacheAndData() {
        // Clear cache and DB before each test
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        configRepo.deleteAll();
    }

    // ─── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cache miss: returns empty list when no configs exist in DB")
    void cacheMiss_noData_returnsEmptyList() {
        List<TriggerConfig> result = cacheService.findActiveKafkaConfigs("nonexistent.topic");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Cache hit: second call does NOT hit repository (served from cache)")
    void cacheHit_secondCall_servedFromCache() {
        // Given — insert a config directly into DB
        TriggerConfig config = TriggerConfig.builder()
                .scenarioKey("testScenario")
                .processKey("testProcess")
                .displayName("Test Config")
                .triggerType("KAFKA")
                .kafkaTopic("UIP.test.topic.v1")
                .variableMapping("{}")
                .enabled(true)
                .build();
        configRepo.saveAndFlush(config);

        // When — first call hits DB
        List<TriggerConfig> firstCall = cacheService.findActiveKafkaConfigs("UIP.test.topic.v1");
        assertThat(firstCall).hasSize(1);

        // Delete from DB to prove second call comes from cache
        configRepo.deleteAll();
        configRepo.flush();

        // When — second call should be served from cache (DB is empty now)
        List<TriggerConfig> secondCall = cacheService.findActiveKafkaConfigs("UIP.test.topic.v1");

        // Then — still returns 1 result from cache
        assertThat(secondCall).hasSize(1);
        assertThat(secondCall.get(0).getScenarioKey()).isEqualTo("testScenario");
    }

    @Test
    @DisplayName("Cache evict: after evictAll(), next call hits repository again")
    void cacheEvict_afterEvictAll_nextCallHitsRepo() {
        // Given — insert config and populate cache
        TriggerConfig config = TriggerConfig.builder()
                .scenarioKey("evictTestScenario")
                .processKey("evictProcess")
                .displayName("Evict Test")
                .triggerType("KAFKA")
                .kafkaTopic("UIP.evict.topic.v1")
                .variableMapping("{}")
                .enabled(true)
                .build();
        configRepo.saveAndFlush(config);

        // Populate cache
        cacheService.findActiveKafkaConfigs("UIP.evict.topic.v1");
        assertThat(cacheManager.getCache("trigger-configs").get("UIP.evict.topic.v1")).isNotNull();

        // When — evict all cache entries
        cacheService.evictAll();

        // Then — cache entry is removed
        assertThat(cacheManager.getCache("trigger-configs").get("UIP.evict.topic.v1")).isNull();

        // And — next call still works (hits DB again)
        List<TriggerConfig> afterEvict = cacheService.findActiveKafkaConfigs("UIP.evict.topic.v1");
        assertThat(afterEvict).hasSize(1);
        assertThat(afterEvict.get(0).getScenarioKey()).isEqualTo("evictTestScenario");
    }

    @Test
    @DisplayName("Different topics cached independently")
    void differentTopics_cachedIndependently() {
        // Given — two configs for different topics
        TriggerConfig config1 = TriggerConfig.builder()
                .scenarioKey("scenario1")
                .processKey("process1")
                .displayName("Config 1")
                .triggerType("KAFKA")
                .kafkaTopic("UIP.topic1.v1")
                .variableMapping("{}")
                .enabled(true)
                .build();
        TriggerConfig config2 = TriggerConfig.builder()
                .scenarioKey("scenario2")
                .processKey("process2")
                .displayName("Config 2")
                .triggerType("KAFKA")
                .kafkaTopic("UIP.topic2.v1")
                .variableMapping("{}")
                .enabled(true)
                .build();
        configRepo.saveAllAndFlush(List.of(config1, config2));

        // When — cache both topics
        cacheService.findActiveKafkaConfigs("UIP.topic1.v1");
        cacheService.findActiveKafkaConfigs("UIP.topic2.v1");

        // Then — both are cached
        assertThat(cacheManager.getCache("trigger-configs").get("UIP.topic1.v1")).isNotNull();
        assertThat(cacheManager.getCache("trigger-configs").get("UIP.topic2.v1")).isNotNull();

        // Delete all DB data
        configRepo.deleteAll();
        configRepo.flush();

        // Both still return data from cache
        assertThat(cacheService.findActiveKafkaConfigs("UIP.topic1.v1")).hasSize(1);
        assertThat(cacheService.findActiveKafkaConfigs("UIP.topic2.v1")).hasSize(1);
    }

    // ─── Docker CLI helpers ─────────────────────────────────────────────────

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
}
