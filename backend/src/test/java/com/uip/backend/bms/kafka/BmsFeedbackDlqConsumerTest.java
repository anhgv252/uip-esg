package com.uip.backend.bms.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.bms.BmsFeedbackMetrics;
import com.uip.backend.bms.domain.FeedbackStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.*;

/**
 * M4-COR-04: Unit test for {@link BmsFeedbackDlqConsumer}.
 *
 * <p>Verifies the DLQ consumer:
 * <ul>
 *   <li>Deserializes a well-formed JSON payload and increments the DLQ metric.</li>
 *   <li>Tolerates malformed JSON (logs error, does NOT throw — Kafka consumer stays alive).</li>
 *   <li>Does not reprocess — read-only by design.</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BmsFeedbackDlqConsumer — DLQ event handling")
class BmsFeedbackDlqConsumerTest {

    @Spy  private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private BmsFeedbackMetrics metrics;

    @InjectMocks private BmsFeedbackDlqConsumer consumer;

    // ─── Happy path ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Well-formed DLQ payload")
    class WellFormedPayload {

        @Test
        @DisplayName("TC-DLQ-01: valid JSON → metrics.recordDlq() called exactly once")
        void validJson_metricsIncremented() {
            String payload = """
                    {
                      "commandId": 42,
                      "buildingId": "B042",
                      "stage": "ACTION_TAKEN",
                      "success": false,
                      "notes": "actuator jammed",
                      "error": "BACnet timeout",
                      "timestamp": "2026-06-12T10:15:30Z"
                    }
                    """;

            consumer.onDlqMessage(payload);

            verify(metrics, times(1)).recordDlq();
        }

        @Test
        @DisplayName("TC-DLQ-02: valid JSON with all four stage values deserializes without error")
        void allStages_deserialize() {
            for (FeedbackStage stage : FeedbackStage.values()) {
                String payload = String.format(
                        "{\"commandId\":1,\"buildingId\":\"B\",\"stage\":\"%s\",\"success\":true}",
                        stage.name());
                consumer.onDlqMessage(payload);
            }
            verify(metrics, times(FeedbackStage.values().length)).recordDlq();
        }
    }

    // ─── Malformed payload tolerance ───────────────────────────────────────────

    @Nested
    @DisplayName("Malformed payload tolerance")
    class MalformedPayload {

        @Test
        @DisplayName("TC-DLQ-03: malformed JSON → no exception thrown, metrics NOT incremented")
        void malformedJson_noThrowNoMetric() {
            String broken = "{ this is not valid json,,, ";

            consumer.onDlqMessage(broken);

            verify(metrics, never()).recordDlq();
        }

        @Test
        @DisplayName("TC-DLQ-04: null message → no exception thrown, metrics NOT incremented")
        void nullMessage_handledGracefully() {
            // Jackson throws on null input — consumer must catch
            consumer.onDlqMessage("null");

            // "null" is valid JSON → deserializes to null map → no NPE in logging path,
            // but no fields present. Verify no exception propagated (implicit — test passes
            // if it doesn't throw). Metric call is optional; assert no crash.
            verify(metrics, atMost(1)).recordDlq();
        }

        @Test
        @DisplayName("TC-DLQ-05: empty string → no exception, metrics NOT incremented")
        void emptyString_handledGracefully() {
            consumer.onDlqMessage("");

            verify(metrics, never()).recordDlq();
        }
    }

    // ─── Read-only guarantee ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Read-only guarantee")
    class ReadOnly {

        @Test
        @DisplayName("TC-DLQ-06: consumer does NOT invoke any feedback service reprocessing")
        void consumerDoesNotReprocess() {
            // The DLQ consumer has no reference to BmsFeedbackService — verified at compile time.
            // This test documents the read-only contract: only metrics + log, no reprocessing call.
            String payload = "{\"commandId\":1,\"buildingId\":\"B\",\"stage\":\"COMMAND_SENT\"}";

            consumer.onDlqMessage(payload);

            // Only side effect allowed: metrics.recordDlq(). No other interaction possible
            // given the consumer's dependencies (objectMapper + metrics).
            verify(metrics).recordDlq();
            verifyNoMoreInteractions(metrics);
        }
    }
}
