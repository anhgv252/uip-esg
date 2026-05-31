package com.uip.backend.alert.flood;

import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.tenant.context.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for FloodAlertConsumer (FL-IT-01..04).
 *
 * Uses Testcontainers PostgreSQL for real persistence; Redis and Kafka are mocked.
 * Each test calls {@code consumer.consume()} directly (bypassing Kafka listener
 * infrastructure) to exercise the full processing logic: validation, dedup, save,
 * Redis publish, and DLQ fallback.
 */
@Tag("integration")
@SpringBootTest(properties = {
    "security.jwt.secret=test-secret-for-integration-tests-only-32chars",
    "spring.cache.type=simple",
    "uip.tenant.allowed-ids=hcm,hanoi,danang",
    "uip.cagg.alert-refresh-ms=999999999",
    "uip.cagg.sensor-refresh-ms=999999999"
})
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FloodAlertConsumerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test").withUsername("test").withPassword("test");

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
        registry.add("uip.capabilities.multi-tenancy", () -> "true");
    }

    @MockBean @SuppressWarnings("unused") RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused") ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean StringRedisTemplate redisTemplate;  // NOT @SuppressWarnings — we need to stub and verify it
    @MockBean @SuppressWarnings("unused") RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean KafkaTemplate<String, String> kafkaTemplate;  // for DLQ verification

    @Autowired FloodAlertConsumer consumer;
    @Autowired AlertEventRepository alertEventRepository;

    // ─── Shared payload — P0_EMERGENCY, tenantId=hcm ─────────────────────────
    private static final String VALID_P0_PAYLOAD =
            "{\"sensorId\":\"S001\",\"sensorType\":\"water_level\",\"value\":2.5,\"threshold\":2.0," +
            "\"severity\":\"P0_EMERGENCY\",\"tenantId\":\"hcm\",\"district\":\"District 7\"," +
            "\"timestamp\":1700000000000}";

    /**
     * Default stub: Redis dedup check returns {@code true} (not a duplicate).
     * FL-IT-02 overrides this to {@code false} within the test body.
     */
    @BeforeEach
    void setup() {
        // Clean up any stale data from previous tests (handles context-sharing edge cases)
        TenantContext.setCurrentTenant("hcm");
        try {
            alertEventRepository.deleteAll();
        } finally {
            TenantContext.clear();
        }

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        TenantContext.setCurrentTenant("hcm");
        try {
            alertEventRepository.deleteAll();
        } finally {
            TenantContext.clear();
        }
    }

    // ─── FL-IT-01 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FL-IT-01: valid P0 payload persists AlertEvent as CRITICAL")
    void consume_validP0Payload_persistsAlertAsCritical() {
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(VALID_P0_PAYLOAD, ack, FloodAlertConsumer.TOPIC, 0);

        var events = alertEventRepository.findAll();
        assertThat(events).hasSize(1);

        var saved = events.get(0);
        assertThat(saved.getSeverity()).isEqualTo("CRITICAL");
        assertThat(saved.getModule()).isEqualTo("FLOOD");
        assertThat(saved.getTenantId()).isEqualTo("hcm");
        assertThat(saved.getSensorId()).isEqualTo("S001");
        assertThat(saved.getStatus()).isEqualTo("OPEN");

        verify(redisTemplate).convertAndSend(eq("uip:alerts"), anyString());
        verify(ack).acknowledge();
    }

    // ─── FL-IT-02 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FL-IT-02: duplicate alert suppressed — no DB save, ack called")
    void consume_duplicateAlert_suppressedNoSave() {
        // Override @BeforeEach stub: setIfAbsent returns false (duplicate window active)
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(VALID_P0_PAYLOAD, ack, FloodAlertConsumer.TOPIC, 0);
        consumer.consume(VALID_P0_PAYLOAD, ack, FloodAlertConsumer.TOPIC, 0);

        assertThat(alertEventRepository.count()).isZero();
        verify(ack, times(2)).acknowledge();
    }

    // ─── FL-IT-03 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FL-IT-03: unknown tenantId rejected — DLQ send, no DB save")
    void consume_unknownTenantId_sentToDlq() {
        String unknownTenantPayload =
                "{\"sensorId\":\"S002\",\"sensorType\":\"water_level\",\"value\":3.0,\"threshold\":2.0," +
                "\"severity\":\"P1_WARNING\",\"tenantId\":\"unknown-tenant\",\"district\":\"District 1\"," +
                "\"timestamp\":1700000000000}";

        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(unknownTenantPayload, ack, FloodAlertConsumer.TOPIC, 0);

        assertThat(alertEventRepository.count()).isZero();
        verify(kafkaTemplate).send(eq("UIP.flink.alert.flood.v1.dlq"), anyString());
        verify(ack).acknowledge();
    }

    // ─── FL-IT-04 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FL-IT-04: invalid JSON at max retries — DLQ send, ack called")
    void consume_invalidJson_afterMaxRetries_sentToDlq() {
        Acknowledgment ack = mock(Acknowledgment.class);

        // retryCount=2 → attempt #3; (retryCount+1=3) >= MAX_RETRIES(3) → DLQ + ack
        consumer.consume("not-valid-json", ack, FloodAlertConsumer.TOPIC, 2);

        verify(kafkaTemplate).send(eq("UIP.flink.alert.flood.v1.dlq"), anyString());
        verify(ack).acknowledge();
    }
}
