package com.uip.backend.bms.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.bms.api.dto.BmsReadingEvent;
import com.uip.backend.kafka.producer.DualPublishKafkaProducer;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BmsReadingKafkaProducer — unit")
class BmsReadingKafkaProducerTest {

    @Mock private KafkaTemplate<String, BmsReadingEvent> kafkaTemplate;
    @Mock private KafkaTemplate<String, GenericRecord>   avroKafkaTemplate;
    @Mock private DualPublishKafkaProducer               dualPublish;

    private BmsReadingKafkaProducer producer;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        producer = new BmsReadingKafkaProducer(kafkaTemplate, Optional.of(dualPublish), objectMapper);
    }

    private BmsReadingEvent buildEvent() {
        return new BmsReadingEvent(
                UUID.randomUUID(), "hcm", "temperature",
                23.5, "°C", Instant.now(), "MODBUS_TCP"
        );
    }

    @Test
    @DisplayName("publish — sends to v1 JSON topic on success")
    void publish_success_v1Json() {
        when(kafkaTemplate.send(eq(BmsReadingKafkaProducer.TOPIC_V1), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publish(buildEvent());

        verify(kafkaTemplate).send(eq(BmsReadingKafkaProducer.TOPIC_V1), anyString(), any());
        verify(kafkaTemplate, never()).send(contains("dlq"), anyString(), any());
    }

    @Test
    @DisplayName("publish — also invokes dualPublish for v2 Avro")
    void publish_success_invokesV2Avro() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publish(buildEvent());

        verify(dualPublish).publish(
                eq(BmsReadingKafkaProducer.TOPIC_V1),
                eq(BmsReadingKafkaProducer.TOPIC_V2),
                anyString(), anyString(),
                eq("avro/BmsReadingEvent.avsc"),
                anyMap()
        );
    }

    @Test
    @DisplayName("publish — falls back to DLQ on v1 topic failure (no v2 attempt)")
    void publish_v1Fails_sendsToDlq_noV2() {
        when(kafkaTemplate.send(eq(BmsReadingKafkaProducer.TOPIC_V1), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(contains("dlq"), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publish(buildEvent());

        verify(kafkaTemplate).send(contains("dlq"), anyString(), any());
        verifyNoInteractions(dualPublish); // v2 skipped when v1 fails
    }

    @Test
    @DisplayName("publishToDlq — sends to DLQ topic")
    void publishToDlq_sendsToDlq() {
        when(kafkaTemplate.send(contains("dlq"), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishToDlq(buildEvent(), "Circuit breaker open");

        verify(kafkaTemplate).send(contains("dlq"), anyString(), any());
    }

    @Test
    @DisplayName("publish — key is tenantId:deviceId")
    void publish_keyFormat() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(kafkaTemplate.send(anyString(), keyCaptor.capture(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        BmsReadingEvent event = buildEvent();
        producer.publish(event);

        assertThat(keyCaptor.getValue()).isEqualTo(event.tenantId() + ":" + event.deviceId());
    }
}
