package com.uip.backend.alert.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Error-path tests for Kafka producer (DLQ) in {@link AlertEventKafkaConsumer}.
 *
 * Verifies compensating actions when KafkaTemplate.send() throws:
 *  - error is logged (no exception propagated to caller)
 *  - offset IS advanced (ack called) so no alert is permanently lost
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertKafkaProducer — Error Paths (DLQ)")
class AlertKafkaProducerErrorTest {

    @Mock private AlertEventRepository        alertEventRepository;
    @Mock private StringRedisTemplate         redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ObjectMapper                objectMapper;
    @Mock private Acknowledgment              ack;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private AlertEventKafkaConsumer consumer;

    @BeforeEach
    void setupRedis() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    // ─── DLQ send failure — compensating action ───────────────────────────────

    @Test
    @DisplayName("when KafkaTemplate.send() throws → error is logged, offset IS advanced (no alert lost)")
    void kafkaSendThrows_errorLoggedAndOffsetAdvanced() throws Exception {
        // Arrange: DB save throws → triggers DLQ path at max retries
        when(alertEventRepository.save(any(AlertEvent.class)))
                .thenThrow(new RuntimeException("DB connection timeout"));

        // KafkaTemplate.send() to DLQ also throws
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"sensorId\":\"ENV-001\"}");
        when(kafkaTemplate.send(eq(AlertEventKafkaConsumer.DLQ_TOPIC), anyString()))
                .thenThrow(new RuntimeException("Kafka broker unreachable"));

        // Act: retryCount=2 → (2+1)=3 >= MAX_RETRIES(3) → DLQ path triggered
        consumer.consume(fullPayload(), ack, AlertEventKafkaConsumer.TOPIC, 2);

        // Assert: offset advanced even though DLQ send failed (compensating action)
        verify(ack).acknowledge();
        // DLQ send was attempted
        verify(kafkaTemplate).send(eq(AlertEventKafkaConsumer.DLQ_TOPIC), anyString());
    }

    @Test
    @DisplayName("when DB throws at max retries → alert sent to DLQ and offset advanced")
    void dbThrowsAtMaxRetries_sendsToDlqAndAdvancesOffset() throws Exception {
        when(alertEventRepository.save(any())).thenThrow(new RuntimeException("DB down"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"fallback\":true}");
        when(kafkaTemplate.send(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // retryCount=2 → max retries reached
        consumer.consume(fullPayload(), ack, AlertEventKafkaConsumer.TOPIC, 2);

        verify(kafkaTemplate).send(eq(AlertEventKafkaConsumer.DLQ_TOPIC), anyString());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("when DB throws below max retries → DLQ NOT sent, offset NOT advanced (retry eligible)")
    void dbThrowsBelowMaxRetries_noAckNoRetry() {
        when(alertEventRepository.save(any())).thenThrow(new RuntimeException("Transient error"));

        // retryCount=0 → (0+1)=1 < MAX_RETRIES(3) → no DLQ, no ack
        consumer.consume(fullPayload(), ack, AlertEventKafkaConsumer.TOPIC, 0);

        verify(kafkaTemplate, never()).send(anyString(), anyString());
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("when objectMapper.writeValueAsString() throws → DLQ skipped, offset still advanced")
    void objectMapperThrowsDuringDlq_offsetStillAdvanced() throws Exception {
        when(alertEventRepository.save(any())).thenThrow(new RuntimeException("DB error"));
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialization fail") {});

        // retryCount=2 → DLQ path; serialization fails → inner catch swallows it
        consumer.consume(fullPayload(), ack, AlertEventKafkaConsumer.TOPIC, 2);

        // DLQ send never reached, but ack is still called after inner catch
        verify(kafkaTemplate, never()).send(anyString(), anyString());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("when KafkaTemplate.send() throws RuntimeException → exception does not propagate to caller")
    void kafkaSendThrowsRuntimeException_doesNotPropagateToConsumer() throws Exception {
        when(alertEventRepository.save(any())).thenThrow(new RuntimeException("persistence failure"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString()))
                .thenThrow(new RuntimeException("Kafka send failed"));

        // Must not throw from the consumer method — error is only logged
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> consumer.consume(fullPayload(), ack, AlertEventKafkaConsumer.TOPIC, 2));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Map<String, Object> fullPayload() {
        Map<String, Object> m = new HashMap<>();
        m.put("sensorId",    "ENV-001");
        m.put("module",      "environment");
        m.put("measureType", "AQI");
        m.put("value",       "155.0");
        m.put("threshold",   "100.0");
        m.put("severity",    "CRITICAL");
        m.put("detectedAt",  java.time.Instant.now().toString());
        return m;
    }
}
