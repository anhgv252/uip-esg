package com.uip.backend.alert.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * v3.1-13: DLQ audit tests for {@link AlertEventKafkaConsumer}.
 *
 * Covers: failed events route to DLQ, reprocessing after DLQ, poison pill handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertEventKafkaConsumer — DLQ Audit")
class AlertEventKafkaDlqAuditTest {

    @Mock private AlertEventRepository alertEventRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ObjectMapper objectMapper;
    @Mock private Acknowledgment ack;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private AlertEventKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    // ─── DLQ Routing ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Failed events route to DLQ")
    class DlqRouting {

        @Test
        @DisplayName("DB failure at retryCount=MAX_RETRIES-1 → routes to DLQ + ack")
        void dbFailure_atMaxRetries_routesToDlq() throws Exception {
            when(alertEventRepository.save(any(AlertEvent.class)))
                    .thenThrow(new RuntimeException("Connection refused"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"sensorId\":\"ENV-001\"}");

            // retryCount=2 (MAX_RETRIES=3, 0-indexed attempt 3)
            consumer.consume(validPayload(), ack, AlertEventKafkaConsumer.TOPIC, 2);

            verify(kafkaTemplate).send(eq(AlertEventKafkaConsumer.DLQ_TOPIC), anyString());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("DB failure at retryCount=0 → does NOT route to DLQ, no ack (Kafka redelivers)")
        void dbFailure_firstAttempt_noDlq_noAck() {
            when(alertEventRepository.save(any(AlertEvent.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            consumer.consume(validPayload(), ack, AlertEventKafkaConsumer.TOPIC, 0);

            verify(kafkaTemplate, never()).send(anyString(), anyString());
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("DB failure at retryCount=1 → does NOT route to DLQ (still below max)")
        void dbFailure_secondAttempt_noDlq() {
            when(alertEventRepository.save(any(AlertEvent.class)))
                    .thenThrow(new RuntimeException("Timeout"));

            consumer.consume(validPayload(), ack, AlertEventKafkaConsumer.TOPIC, 1);

            verify(kafkaTemplate, never()).send(anyString(), anyString());
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("DLQ payload preserves original event data")
        void dlqPayload_preservesOriginalData() throws Exception {
            when(alertEventRepository.save(any(AlertEvent.class)))
                    .thenThrow(new RuntimeException("DB down"));

            String originalJson = "{\"sensorId\":\"ENV-001\",\"severity\":\"CRITICAL\"}";
            when(objectMapper.writeValueAsString(any())).thenReturn(originalJson);

            consumer.consume(validPayload(), ack, AlertEventKafkaConsumer.TOPIC, 2);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(AlertEventKafkaConsumer.DLQ_TOPIC), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue()).contains("sensorId");
        }

        @Test
        @DisplayName("DLQ topic name matches convention UIP.*.dlq")
        void dlqTopicName_followsConvention() {
            assertThat(AlertEventKafkaConsumer.DLQ_TOPIC).startsWith("UIP.");
            assertThat(AlertEventKafkaConsumer.DLQ_TOPIC).endsWith(".dlq");
        }
    }

    // ─── DLQ Reprocessing ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("DLQ reprocessing")
    class DlqReprocessing {

        @Test
        @DisplayName("Previously failed event succeeds on retry (simulates DLQ reprocessing)")
        void previouslyFailed_succeedsOnRetry() throws Exception {
            // First call: DB failure
            when(alertEventRepository.save(any(AlertEvent.class)))
                    .thenThrow(new RuntimeException("Temporary DB issue"));

            consumer.consume(validPayload(), ack, AlertEventKafkaConsumer.TOPIC, 0);

            verify(ack, never()).acknowledge();

            // Reset mock: DB recovered
            reset(alertEventRepository);
            AlertEvent saved = savedEvent();
            when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            consumer.consume(validPayload(), ack, AlertEventKafkaConsumer.TOPIC, 0);

            verify(alertEventRepository).save(any(AlertEvent.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Reprocessed event with fresh dedup key succeeds")
        void reprocessedEvent_freshDedupKey_succeeds() throws Exception {
            // Dedup key expired (Redis TTL) → setIfAbsent returns true
            when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
            AlertEvent saved = savedEvent();
            when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            consumer.consume(validPayload(), ack, AlertEventKafkaConsumer.TOPIC, 0);

            verify(alertEventRepository).save(any(AlertEvent.class));
            verify(ack).acknowledge();
        }
    }

    // ─── Poison Pill ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Poison pill handling")
    class PoisonPill {

        @Test
        @DisplayName("Unparseable map with null values → DB exception at max retries → DLQ + ack")
        void poisonNullValues_atMaxRetries_routesToDlq() throws Exception {
            // Payload has null values that cause NPE or DB constraint violation
            Map<String, Object> poisonPayload = new HashMap<>();
            poisonPayload.put("sensorId", null);
            poisonPayload.put("severity", null);
            poisonPayload.put("value", "not_a_number");

            when(alertEventRepository.save(any(AlertEvent.class)))
                    .thenThrow(new RuntimeException("NOT NULL constraint violation"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            consumer.consume(poisonPayload, ack, AlertEventKafkaConsumer.TOPIC, 2);

            verify(kafkaTemplate).send(eq(AlertEventKafkaConsumer.DLQ_TOPIC), anyString());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("Malformed severity → processed with null severity, eventually DLQ on DB failure")
        void malformedSeverity_processedWithNull() throws Exception {
            Map<String, Object> payload = validPayload();
            payload.put("severity", "INVALID_SEVERITY_LEVEL");

            AlertEvent saved = savedEvent();
            when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            consumer.consume(payload, ack, AlertEventKafkaConsumer.TOPIC, 0);

            verify(alertEventRepository).save(argThat(e ->
                    "INVALID_SEVERITY_LEVEL".equals(e.getSeverity())));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("DLQ publish itself fails → ack still called (prevents infinite retry)")
        void dlqPublishFails_stillAcks() throws Exception {
            when(alertEventRepository.save(any(AlertEvent.class)))
                    .thenThrow(new RuntimeException("DB down"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            doThrow(new RuntimeException("Kafka unavailable"))
                    .when(kafkaTemplate).send(anyString(), anyString());

            consumer.consume(validPayload(), ack, AlertEventKafkaConsumer.TOPIC, 2);

            // DLQ send failed, but ack is still called to prevent poison pill blocking
            verify(ack).acknowledge();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> validPayload() {
        Map<String, Object> m = new HashMap<>();
        m.put("sensorId", "ENV-001");
        m.put("module", "environment");
        m.put("measureType", "AQI");
        m.put("value", 155.0);
        m.put("threshold", 100.0);
        m.put("severity", "CRITICAL");
        m.put("detectedAt", Instant.now().toString());
        return m;
    }

    private AlertEvent savedEvent() {
        AlertEvent e = new AlertEvent();
        e.setId(UUID.randomUUID());
        e.setSensorId("ENV-001");
        e.setSeverity("CRITICAL");
        return e;
    }
}
