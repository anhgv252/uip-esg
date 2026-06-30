package com.uip.backend.safety.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.common.spi.AlertPort;
import com.uip.backend.common.spi.AlertPort.SavedAlertSnapshot;
import com.uip.backend.common.spi.AlertPort.StructuralAlertInput;
import com.uip.backend.common.spi.NotificationPort;
import com.uip.backend.safety.service.BuildingSafetyService;
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
 * Unit tests for StructuralAlertConsumer — severity mapping, dedup, DLQ, BR-010 compliance.
 *
 * <p>After ADR-052 migration C1+C2, the consumer depends on {@link AlertPort} /
 * {@link NotificationPort} (not on {@code AlertService} / {@code NotificationRouter}).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StructuralAlertConsumer — unit")
class StructuralAlertConsumerTest {

    @Mock private AlertPort                     alertPort;
    @Mock private BuildingSafetyService         buildingSafetyService;
    @Mock private NotificationPort              notificationPort;
    @Mock private StringRedisTemplate           redisTemplate;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private Acknowledgment                ack;

    private StructuralAlertConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String VALID_PAYLOAD = """
            {
              "eventId": "evt-001",
              "sensorId": "SENSOR-VIBR-001",
              "sensorType": "STRUCTURAL_VIBRATION",
              "tenantId": "hcm",
              "buildingId": "BLDG-001",
              "measuredValue": 55.0,
              "thresholdValue": 50.0,
              "severity": "CRITICAL",
              "district": "Quận 1",
              "observedAtMillis": 1748851200000,
              "consecutiveSpikes": 3,
              "requiresOperatorReview": true
            }
            """;

    private SavedAlertSnapshot savedSnapshot() {
        return new SavedAlertSnapshot(
                UUID.randomUUID(), "SENSOR-VIBR-001", "CRITICAL", "BLDG-001", "hcm");
    }

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        consumer = new StructuralAlertConsumer(
                alertPort, buildingSafetyService,
                notificationPort, redisTemplate, kafkaTemplate, objectMapper);
        ReflectionTestUtils.setField(consumer, "allowedTenantsConfig", "hcm,hanoi,danang");
    }

    // ─── Severity Mapping ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Severity Mapping")
    class SeverityMapping {

        @Test @DisplayName("CRITICAL → CRITICAL")
        void critical() { assertThat(StructuralAlertConsumer.mapSeverity("CRITICAL")).isEqualTo("CRITICAL"); }

        @Test @DisplayName("WARNING → HIGH")
        void warning() { assertThat(StructuralAlertConsumer.mapSeverity("WARNING")).isEqualTo("HIGH"); }

        @Test @DisplayName("null → WARNING (fallback)")
        void nullFallback() { assertThat(StructuralAlertConsumer.mapSeverity(null)).isEqualTo("WARNING"); }

        @Test @DisplayName("unknown → WARNING (fallback)")
        void unknownFallback() { assertThat(StructuralAlertConsumer.mapSeverity("UNKNOWN")).isEqualTo("WARNING"); }
    }

    // ─── Happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid CRITICAL payload → persists, evicts cache, routes notification, acks")
    void validPayload_fullPipeline() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(alertPort.saveStructuralAlert(any(StructuralAlertInput.class))).thenReturn(savedSnapshot());

        consumer.consume(VALID_PAYLOAD, ack, StructuralAlertConsumer.TOPIC, 0);

        // persisted via port with correct module + building + severity
        ArgumentCaptor<StructuralAlertInput> captor = ArgumentCaptor.forClass(StructuralAlertInput.class);
        verify(alertPort).saveStructuralAlert(captor.capture());
        assertThat(captor.getValue().module()).isEqualTo("STRUCTURAL");
        assertThat(captor.getValue().buildingId()).isEqualTo("BLDG-001");
        assertThat(captor.getValue().severity()).isEqualTo("CRITICAL");

        // safety score cache evicted for the building
        verify(buildingSafetyService).evictSafetyScore("BLDG-001");

        // notification dispatched via port with structural category + critical severity
        verify(notificationPort).routeAlert(
                eq("SENSOR-VIBR-001"), eq("STRUCTURAL"), eq("CRITICAL"), contains("operator"), eq("hcm"));

        verify(ack).acknowledge();
    }

    // ─── Tenant validation ───────────────────────────────────────────────────

    @Test
    @DisplayName("Unknown tenant → sent to DLQ, no persistence")
    void unknownTenant_dlq() throws Exception {
        String payload = VALID_PAYLOAD.replace("\"hcm\"", "\"unknown-tenant\"");
        consumer.consume(payload, ack, StructuralAlertConsumer.TOPIC, 0);

        verify(kafkaTemplate).send(eq("UIP.structural.alert.dlq.v1"), anyString());
        verifyNoInteractions(alertPort);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Null tenant → sent to DLQ")
    void nullTenant_dlq() throws Exception {
        String payload = VALID_PAYLOAD.replace("\"tenantId\": \"hcm\"", "\"tenantId\": null");
        consumer.consume(payload, ack, StructuralAlertConsumer.TOPIC, 0);

        verify(kafkaTemplate).send(eq("UIP.structural.alert.dlq.v1"), anyString());
        verifyNoInteractions(alertPort);
        verify(ack).acknowledge();
    }

    // ─── Deduplication ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Duplicate within 1 min → suppressed, no persistence")
    void duplicate_suppressed() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);
        consumer.consume(VALID_PAYLOAD, ack, StructuralAlertConsumer.TOPIC, 0);

        verifyNoInteractions(alertPort);
        verify(ack).acknowledge();
    }

    // ─── BR-010: safety constraint ───────────────────────────────────────────

    @Test
    @DisplayName("BR-010: mapSeverity never produces 'EVACUATE' or action-triggering value")
    void br010_noAutoEvacuateSeverity() {
        // Verify that no severity value can trigger auto-evacuation
        for (String input : new String[]{"CRITICAL", "WARNING", "P0", "EMERGENCY", null}) {
            String mapped = StructuralAlertConsumer.mapSeverity(input);
            assertThat(mapped).isIn("CRITICAL", "HIGH", "WARNING");
        }
    }

    // ─── MVP5-S1-T06: cross-tenant dedup isolation ───────────────────────────

    @Test
    @DisplayName("MVP5-S1-T06: tenant A dedup does NOT suppress tenant B (same sensorId)")
    void crossTenantDedup_isolated() throws Exception {
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(alertPort.saveStructuralAlert(any(StructuralAlertInput.class))).thenReturn(savedSnapshot());

        // Tenant A (hcm)
        consumer.consume(VALID_PAYLOAD, ack, StructuralAlertConsumer.TOPIC, 0);
        // Tenant B (hanoi) — swap tenantId in the payload, same sensor/building
        String payloadHanoi = VALID_PAYLOAD.replace("\"tenantId\": \"hcm\"", "\"tenantId\": \"hanoi\"")
                                           .replace("\"hcm\"", "\"hanoi\"");
        consumer.consume(payloadHanoi, ack, StructuralAlertConsumer.TOPIC, 0);

        // Both alerts persisted — two tenant-scoped dedup keys
        verify(alertPort, times(2)).saveStructuralAlert(any(StructuralAlertInput.class));
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps, times(2)).setIfAbsent(keyCaptor.capture(), anyString(), any());
        assertThat(keyCaptor.getAllValues().get(0)).contains("tenant:hcm:");
        assertThat(keyCaptor.getAllValues().get(1)).contains("tenant:hanoi:");
    }
}
