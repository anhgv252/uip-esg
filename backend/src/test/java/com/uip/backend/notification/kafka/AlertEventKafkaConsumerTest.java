package com.uip.backend.notification.kafka;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertEventKafkaConsumer")
class AlertEventKafkaConsumerTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private AlertEventKafkaConsumer consumer;

    // -------------------------------------------------------------------------
    // consume — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("consume: valid payload → persists, publishes, acks")
    void consume_validPayload_persistsAndAcks() {
        Map<String, Object> payload = fullPayload();
        AlertEvent saved = new AlertEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);

        consumer.consume(payload, ack);

        verify(alertEventRepository).save(any(AlertEvent.class));
        verify(notificationService).publishAlert(saved);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: numeric value as Double → parses correctly")
    void consume_numericValue_parsedAsDouble() {
        Map<String, Object> payload = fullPayload();
        payload.put("value", 55.2);
        payload.put("threshold", 50.0);

        AlertEvent saved = new AlertEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);

        consumer.consume(payload, ack);

        verify(alertEventRepository).save(argThat((AlertEvent e) ->
                e.getValue() != null && e.getValue() == 55.2
        ));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: numeric value as Integer → promoted to Double")
    void consume_integerValue_promotedToDouble() {
        Map<String, Object> payload = fullPayload();
        payload.put("value", 42);

        AlertEvent saved = new AlertEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);

        consumer.consume(payload, ack);

        verify(alertEventRepository).save(argThat((AlertEvent e) ->
                e.getValue() != null && e.getValue() == 42.0
        ));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: numeric value as String → parsed to Double")
    void consume_stringValue_parsedToDouble() {
        Map<String, Object> payload = fullPayload();
        payload.put("value", "99.9");

        AlertEvent saved = new AlertEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);

        consumer.consume(payload, ack);

        verify(alertEventRepository).save(argThat((AlertEvent e) ->
                e.getValue() != null && e.getValue() == 99.9
        ));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: invalid numeric string → value is null")
    void consume_invalidNumericString_valueNull() {
        Map<String, Object> payload = fullPayload();
        payload.put("value", "not-a-number");

        AlertEvent saved = new AlertEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);

        consumer.consume(payload, ack);

        verify(alertEventRepository).save(argThat((AlertEvent e) -> e.getValue() == null));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: missing detectedAt → defaults to Instant.now")
    void consume_missingDetectedAt_defaultsToNow() {
        Map<String, Object> payload = fullPayload();
        payload.remove("detectedAt");

        Instant before = Instant.now();
        AlertEvent saved = new AlertEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);

        consumer.consume(payload, ack);

        Instant after = Instant.now();
        verify(alertEventRepository).save(argThat((AlertEvent e) ->
                e.getDetectedAt() != null
                && !e.getDetectedAt().isBefore(before)
                && !e.getDetectedAt().isAfter(after)
        ));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consume: null field values → saves with nulls, still acks")
    void consume_nullFields_savesWithNulls() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sensorId", null);
        payload.put("value", null);

        AlertEvent saved = new AlertEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);

        consumer.consume(payload, ack);

        verify(alertEventRepository).save(argThat((AlertEvent e) ->
                e.getSensorId() == null && e.getValue() == null
        ));
        verify(ack).acknowledge();
    }

    // -------------------------------------------------------------------------
    // consume — error handling
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("consume: repository throws → does NOT ack (retry eligible)")
    void consume_repositoryThrows_doesNotAck() {
        Map<String, Object> payload = fullPayload();
        when(alertEventRepository.save(any(AlertEvent.class)))
                .thenThrow(new RuntimeException("DB timeout"));

        consumer.consume(payload, ack);

        verify(notificationService, never()).publishAlert(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("consume: notification publish throws → does NOT ack")
    void consume_notificationThrows_doesNotAck() {
        Map<String, Object> payload = fullPayload();
        AlertEvent saved = new AlertEvent();
        when(alertEventRepository.save(any(AlertEvent.class))).thenReturn(saved);
        doThrow(new RuntimeException("Redis unavailable"))
                .when(notificationService).publishAlert(any());

        consumer.consume(payload, ack);

        verify(ack, never()).acknowledge();
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
}
