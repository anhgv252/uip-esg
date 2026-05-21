package com.uip.backend.workflow.config;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "security.jwt.secret=test-secret-for-integration-tests-only-32chars",
        "spring.cache.type=simple",
        "uip.cagg.alert-refresh-ms=999999999",
        "uip.cagg.sensor-refresh-ms=999999999",
        "spring.autoconfigure.exclude=" +
            "org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration," +
            "org.camunda.bpm.spring.boot.starter.rest.CamundaBpmRestJerseyAutoConfiguration," +
            "org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration," +
            "org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
    }
)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@DisplayName("MVP2-03b TriggerConfigCacheService — Spring Cache IT")
class TriggerConfigCacheServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("spring.data.redis.host", () -> "localhost");
    }

    @MockBean @SuppressWarnings("unused") RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused") ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused") StringRedisTemplate redisTemplate;
    @MockBean @SuppressWarnings("unused") RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused") KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean @SuppressWarnings("unused") RuntimeService runtimeService;
    @MockBean @SuppressWarnings("unused") RepositoryService repositoryService;
    @MockBean @SuppressWarnings("unused") HistoryService historyService;

    @Autowired
    private TriggerConfigCacheService cacheService;

    @Autowired
    private TriggerConfigRepository configRepo;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCacheAndData() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        configRepo.deleteAll();
    }

    @Test
    @DisplayName("Cache miss: returns empty list when no configs exist in DB")
    void cacheMiss_noData_returnsEmptyList() {
        List<TriggerConfig> result = cacheService.findActiveKafkaConfigs("nonexistent.topic");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Cache hit: second call does NOT hit repository (served from cache)")
    void cacheHit_secondCall_servedFromCache() {
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

        List<TriggerConfig> firstCall = cacheService.findActiveKafkaConfigs("UIP.test.topic.v1");
        assertThat(firstCall).hasSize(1);

        configRepo.deleteAll();
        configRepo.flush();

        List<TriggerConfig> secondCall = cacheService.findActiveKafkaConfigs("UIP.test.topic.v1");
        assertThat(secondCall).hasSize(1);
        assertThat(secondCall.get(0).getScenarioKey()).isEqualTo("testScenario");
    }

    @Test
    @DisplayName("Cache evict: after evictAll(), next call hits repository again")
    void cacheEvict_afterEvictAll_nextCallHitsRepo() {
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

        cacheService.findActiveKafkaConfigs("UIP.evict.topic.v1");
        assertThat(cacheManager.getCache("trigger-configs").get("UIP.evict.topic.v1")).isNotNull();

        cacheService.evictAll();

        assertThat(cacheManager.getCache("trigger-configs").get("UIP.evict.topic.v1")).isNull();

        List<TriggerConfig> afterEvict = cacheService.findActiveKafkaConfigs("UIP.evict.topic.v1");
        assertThat(afterEvict).hasSize(1);
        assertThat(afterEvict.get(0).getScenarioKey()).isEqualTo("evictTestScenario");
    }

    @Test
    @DisplayName("Different topics cached independently")
    void differentTopics_cachedIndependently() {
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

        cacheService.findActiveKafkaConfigs("UIP.topic1.v1");
        cacheService.findActiveKafkaConfigs("UIP.topic2.v1");

        assertThat(cacheManager.getCache("trigger-configs").get("UIP.topic1.v1")).isNotNull();
        assertThat(cacheManager.getCache("trigger-configs").get("UIP.topic2.v1")).isNotNull();

        configRepo.deleteAll();
        configRepo.flush();

        assertThat(cacheService.findActiveKafkaConfigs("UIP.topic1.v1")).hasSize(1);
        assertThat(cacheService.findActiveKafkaConfigs("UIP.topic2.v1")).hasSize(1);
    }
}
