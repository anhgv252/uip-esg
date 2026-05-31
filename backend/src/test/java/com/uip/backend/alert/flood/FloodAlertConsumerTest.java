package com.uip.backend.alert.flood;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FloodAlertConsumer — mapSeverity + consume/dedup/DLQ paths.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FloodAlertConsumer — unit")
class FloodAlertConsumerTest {

    @Mock private AlertEventRepository alertEventRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private Acknowledgment ack;

    private FloodAlertConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        consumer = new FloodAlertConsumer(alertEventRepository, redisTemplate, kafkaTemplate, objectMapper);
        ReflectionTestUtils.setField(consumer, "allowedTenantsConfig", "hcm,hanoi,danang");
    }

    // ─── Severity mapping ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Severity Mapping")
    class SeverityMapping {

        @Test
        @DisplayName("mapSeverity — P0_EMERGENCY → CRITICAL")
        void p0ToCritical() {
            assertThat(FloodAlertConsumer.mapSeverity("P0_EMERGENCY")).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("mapSeverity — P1_WARNING → HIGH")
        void p1ToHigh() {
            assertThat(FloodAlertConsumer.mapSeverity("P1_WARNING")).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("mapSeverity — P2_ADVISORY → WARNING")
        void p2ToWarning() {
            assertThat(FloodAlertConsumer.mapSeverity("P2_ADVISORY")).isEqualTo("WARNING");
        }

        @Test
        @DisplayName("mapSeverity — null → WARNING (fallback)")
        void nullToWarning() {
            assertThat(FloodAlertConsumer.mapSeverity(null)).isEqualTo("WARNING");
        }

        @Test
        @DisplayName("mapSeverity — unknown value → WARNING (fallback)")
        void unknownToWarning() {
            assertThat(FloodAlertConsumer.mapSeverity("P3_CUSTOM")).isEqualTo("WARNING");
        }
    }

    // ─── Consume path ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Consume Path")
    class ConsumePath {

        private String validPayload(String tenantId, String severity) throws Exception {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "sensorId", "SENSOR-001",
                    "sensorType", "RAINFALL",
                    "value", 95.0,
                    "threshold", 80.0,
                    "severity", severity,
                    "tenantId", tenantId,
                    "district", "district-7",
                    "timestamp", System.currentTimeMillis()
            ));
        }

        @Test
        @DisplayName("FL-T-06: valid payload → AlertEvent saved + Redis published + ack")
        void validPayload_savesAndPublishes() throws Exception {
            AlertEvent saved = new AlertEvent();
            saved.setId(UUID.randomUUID());
            saved.setSensorId("SENSOR-001");
            saved.setModule("FLOOD");
            saved.setSeverity("HIGH");
            saved.setStatus("OPEN");
            saved.setMeasureType("RAINFALL");
            saved.setValue(95.0);

            when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);
            when(alertEventRepository.save(any())).thenReturn(saved);

            consumer.consume(validPayload("hcm", "P1_WARNING"), ack, "UIP.flink.alert.flood.v1", 0);

            verify(alertEventRepository).save(any(AlertEvent.class));
            verify(redisTemplate).convertAndSend(eq("uip:alerts"), anyString());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("FL-T-07: duplicate dedup → suppressed, no DB save, ack")
        void duplicate_suppressed() throws Exception {
            when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.FALSE);

            consumer.consume(validPayload("hcm", "P1_WARNING"), ack, "UIP.flink.alert.flood.v1", 0);

            verify(alertEventRepository, never()).save(any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("FL-T-08: unknown tenantId → DLQ, no DB save, ack")
        void unknownTenantId_sentToDLQ() throws Exception {
            consumer.consume(validPayload("unknown_tenant", "P0_EMERGENCY"), ack, "UIP.flink.alert.flood.v1", 0);

            verify(kafkaTemplate).send(eq("UIP.flink.alert.flood.v1.dlq"), anyString());
            verify(alertEventRepository, never()).save(any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("FL-T-09: invalid JSON + retryCount=2 (MAX_RETRIES-1) → DLQ + ack")
        void invalidJson_maxRetries_sentToDLQ() {
            consumer.consume("{invalid-json", ack, "UIP.flink.alert.flood.v1", 2);

            verify(kafkaTemplate).send(eq("UIP.flink.alert.flood.v1.dlq"), eq("{invalid-json"));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("FL-T-10: invalid JSON + retryCount=0 → no ack (Kafka redelivery)")
        void invalidJson_firstRetry_noAck() {
            consumer.consume("{bad}", ack, "UIP.flink.alert.flood.v1", 0);

            verify(kafkaTemplate, never()).send(anyString(), anyString());
            verify(ack, never()).acknowledge();
        }

        @Test
        @DisplayName("FL-T-11: severity mapped correctly in saved entity")
        void consume_severityMappedOnSave() throws Exception {
            AlertEvent saved = new AlertEvent();
            saved.setId(UUID.randomUUID());
            saved.setSensorId("SENSOR-001");
            saved.setModule("FLOOD");
            saved.setSeverity("CRITICAL");
            saved.setStatus("OPEN");
            saved.setMeasureType("RAINFALL");
            saved.setValue(95.0);

            when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);
            when(alertEventRepository.save(any())).thenReturn(saved);

            consumer.consume(validPayload("hcm", "P0_EMERGENCY"), ack, "UIP.flink.alert.flood.v1", 0);

            ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
            verify(alertEventRepository).save(captor.capture());
            assertThat(captor.getValue().getSeverity()).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("FL-T-12: Redis payload contains tenantId (B2-5 push routing)")
        void redisPayload_containsTenantId() throws Exception {
            AlertEvent saved = new AlertEvent();
            saved.setId(UUID.randomUUID());
            saved.setSensorId("SENSOR-001");
            saved.setModule("FLOOD");
            saved.setSeverity("HIGH");
            saved.setStatus("OPEN");
            saved.setMeasureType("RAINFALL");
            saved.setValue(95.0);
            saved.setTenantId("hcm");

            when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(Boolean.TRUE);
            when(alertEventRepository.save(any())).thenReturn(saved);

            consumer.consume(validPayload("hcm", "P1_WARNING"), ack, "UIP.flink.alert.flood.v1", 0);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate).convertAndSend(eq("uip:alerts"), jsonCaptor.capture());
            assertThat(jsonCaptor.getValue()).contains("\"tenantId\"").contains("hcm");
        }
    }
}
