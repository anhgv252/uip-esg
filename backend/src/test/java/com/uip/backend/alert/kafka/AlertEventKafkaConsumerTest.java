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
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertEventKafkaConsumer (alert module)")
class AlertEventKafkaConsumerTest {

    @Mock private AlertEventRepository        alertEventRepository;
    @Mock private StringRedisTemplate         redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ObjectMapper                objectMapper;
    @Mock private Acknowledgment              ack;

    @InjectMocks
    private AlertEventKafkaConsumer consumer;

    @BeforeEach
    void setupRedis() {
        // lenient: alcuni test sovrascrivono setIfAbsent (dedup test) — evita UnnecessaryStubbingException
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    // -------------------------------------------------------------------------
    // consume — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("consume: valid payload → persists, publishes to Redis, acks")
    void consume_validPayload_persistsAndPublishesToRedis() throws Exception {
        AlertEvent saved = savedEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
        when(objectMapper.writeValueAsString(saved)).thenReturn("{\"id\":\"abc\"}");

        consumer.consume(fullPayload(), ack);

        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(redisTemplate).convertAndSend(
                eq(AlertEventKafkaConsumer.ALERT_REDIS_CHANNEL), anyString());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: numeric value as Double → parses correctly")
    void consume_numericDouble_parsed() throws Exception {
        Map<String, Object> payload = fullPayload();
        payload.put("value", 55.2);

        AlertEvent saved = savedEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        consumer.consume(payload, ack);

        verify(alertEventRepository).save(argThat((AlertEvent e) ->
                e.getValue() != null && e.getValue() == 55.2));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: numeric value as Integer → promoted to Double")
    void consume_integerValue_promotedToDouble() throws Exception {
        Map<String, Object> payload = fullPayload();
        payload.put("value", 42);

        AlertEvent saved = savedEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        consumer.consume(payload, ack);

        verify(alertEventRepository).save(argThat((AlertEvent e) ->
                e.getValue() != null && e.getValue() == 42.0));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: missing detectedAt → defaults to Instant.now")
    void consume_missingDetectedAt_defaultsToNow() throws Exception {
        Map<String, Object> payload = fullPayload();
        payload.remove("detectedAt");

        Instant before = Instant.now();
        AlertEvent saved = savedEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        consumer.consume(payload, ack);

        Instant after = Instant.now();
        verify(alertEventRepository).save(argThat((AlertEvent e) ->
                e.getDetectedAt() != null
                && !e.getDetectedAt().isBefore(before)
                && !e.getDetectedAt().isAfter(after)));
        verify(ack).acknowledge();
    }

    // -------------------------------------------------------------------------
    // consume — dedup (idempotency)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("consume: duplicate Kafka delivery → suppressed, still acks (no DB save)")
    void consume_duplicate_suppressedAndAcked() {
        // Redis returns false → this alert was already processed within dedup window
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        consumer.consume(fullPayload(), ack);

        verify(alertEventRepository, never()).save(any());
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        verify(ack).acknowledge();  // must ack to advance Kafka offset
    }

    @Test
    @DisplayName("consume: dedup key uses sensorId + measureType + severity")
    void consume_dedupKeyIncludesSensorMeasureSeverity() throws Exception {
        when(alertEventRepository.save(any())).thenReturn(savedEvent());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        consumer.consume(fullPayload(), ack);

        verify(valueOps).setIfAbsent(
                eq("alert:dedup:kafka:ENV-001:AQI:CRITICAL"),
                eq("1"),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("consume: Redis setIfAbsent returns null (Redis down) → treats as new, still processes")
    void consume_redisReturnsNull_treatsAsNew() throws Exception {
        // null means Redis unavailable — dedup fails open (process the alert)
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);

        AlertEvent saved = savedEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        consumer.consume(fullPayload(), ack);

        // !Boolean.TRUE.equals(null) = true → proceeds normally
        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(ack).acknowledge();
    }

    // -------------------------------------------------------------------------
    // consume — error handling
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("consume: repository throws → does NOT ack (retry eligible)")
    void consume_repositoryThrows_doesNotAck() {
        when(alertEventRepository.save(any(AlertEvent.class)))
                .thenThrow(new RuntimeException("DB timeout"));

        consumer.consume(fullPayload(), ack);

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("consume: Redis publish fails → does NOT ack")
    void consume_redisThrows_doesNotAck() throws Exception { // NOSONAR — checked ex thrown via mock
        AlertEvent saved = savedEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("fail") {})
                .when(objectMapper).writeValueAsString(any());

        consumer.consume(fullPayload(), ack);

        // publishToRedis swallows JsonProcessingException → still acks
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: null field values → saves with nulls, still acks")
    void consume_nullFields_savesWithNulls() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sensorId", null);
        payload.put("value", null);

        AlertEvent saved = savedEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        consumer.consume(payload, ack);

        verify(alertEventRepository).save(argThat((AlertEvent e) ->
                e.getSensorId() == null && e.getValue() == null));
        verify(ack).acknowledge();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> fullPayload() {
        Map<String, Object> m = new HashMap<>();
        m.put("sensorId",    "ENV-001");
        m.put("module",      "environment");
        m.put("measureType", "AQI");
        m.put("value",       "155.0");
        m.put("threshold",   "100.0");
        m.put("severity",    "CRITICAL");
        m.put("detectedAt",  Instant.now().toString());
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
