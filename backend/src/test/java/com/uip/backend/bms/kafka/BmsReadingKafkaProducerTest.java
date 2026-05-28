package com.uip.backend.bms.kafka;

import com.uip.backend.bms.api.dto.BmsReadingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BmsReadingKafkaProducer — unit")
class BmsReadingKafkaProducerTest {

    @Mock private KafkaTemplate<String, BmsReadingEvent> kafkaTemplate;

    private BmsReadingKafkaProducer producer;

    @BeforeEach
    void setUp() {
        producer = new BmsReadingKafkaProducer(kafkaTemplate);
    }

    private BmsReadingEvent buildEvent() {
        return new BmsReadingEvent(
                UUID.randomUUID(), "hcm", "temperature",
                23.5, "°C", Instant.now(), "MODBUS_TCP"
        );
    }

    @Test
    @DisplayName("publish — sends to main topic on success")
    void publish_success() {
        when(kafkaTemplate.send(eq("UIP.bms.reading.raw.v1"), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        producer.publish(buildEvent());

        verify(kafkaTemplate).send(eq("UIP.bms.reading.raw.v1"), anyString(), any());
        verify(kafkaTemplate, never()).send(eq("UIP.bms.reading.raw.v1.dlq"), anyString(), any());
    }

    @Test
    @DisplayName("publish — falls back to DLQ on main topic failure")
    void publish_mainFails_sendsToDlq() {
        when(kafkaTemplate.send(eq("UIP.bms.reading.raw.v1"), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(eq("UIP.bms.reading.raw.v1.dlq"), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publish(buildEvent());

        verify(kafkaTemplate).send(eq("UIP.bms.reading.raw.v1.dlq"), anyString(), any());
    }

    @Test
    @DisplayName("publishToDlq — sends to DLQ topic")
    void publishToDlq_sendsToDlq() {
        when(kafkaTemplate.send(eq("UIP.bms.reading.raw.v1.dlq"), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishToDlq(buildEvent(), "Circuit breaker open");

        verify(kafkaTemplate).send(eq("UIP.bms.reading.raw.v1.dlq"), anyString(), any());
    }

    @Test
    @DisplayName("publish — key is tenantId:deviceId")
    void publish_keyFormat() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(kafkaTemplate.send(anyString(), keyCaptor.capture(), any())).thenReturn(CompletableFuture.completedFuture(null));

        BmsReadingEvent event = buildEvent();
        producer.publish(event);

        String key = keyCaptor.getValue();
        assertThat(key).isEqualTo(event.tenantId() + ":" + event.deviceId());
    }
}
