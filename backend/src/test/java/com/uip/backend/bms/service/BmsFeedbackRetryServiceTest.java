package com.uip.backend.bms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.bms.BmsFeedbackMetrics;
import com.uip.backend.bms.domain.FeedbackStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BmsFeedbackRetryService — unit tests")
class BmsFeedbackRetryServiceTest {

    @Mock private BmsFeedbackService                  feedbackService;
    @Mock private KafkaTemplate<String, String>       kafkaTemplate;
    @Mock private BmsFeedbackMetrics                  metrics;

    @InjectMocks private BmsFeedbackRetryService retryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void injectMapper() throws Exception {
        // Inject real ObjectMapper via reflection (InjectMocks uses Mockito mock otherwise)
        var field = BmsFeedbackRetryService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(retryService, objectMapper);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Success on first attempt")
    class SuccessOnFirstAttempt {

        @Test
        @DisplayName("TC-BFR-01: recordStage succeeds → metrics.stageCompleted called, no DLQ publish")
        void recordStageSucceeds_noRetryNoDlq() {
            doNothing().when(feedbackService)
                    .recordStage(anyLong(), anyString(), any(), anyBoolean(), any());
            when(feedbackService.isLoopComplete(1L)).thenReturn(false);

            retryService.recordStageWithRetry(1L, "B001", FeedbackStage.COMMAND_SENT, true, null);

            verify(feedbackService, times(1))
                    .recordStage(1L, "B001", FeedbackStage.COMMAND_SENT, true, null);
            verify(metrics).recordStageCompleted(FeedbackStage.COMMAND_SENT);
            verify(kafkaTemplate, never()).send(any(), any(), any());
        }

        @Test
        @DisplayName("TC-BFR-02: when loop completes → metrics.loopComplete called")
        void loopComplete_metricsRecordLoopComplete() {
            doNothing().when(feedbackService)
                    .recordStage(anyLong(), anyString(), any(), anyBoolean(), any());
            when(feedbackService.isLoopComplete(1L)).thenReturn(true);

            retryService.recordStageWithRetry(1L, "B001", FeedbackStage.FEEDBACK_VERIFIED, true, "OK");

            verify(metrics).recordLoopComplete();
        }
    }

    // ── Retry logic ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Retry attempts")
    class RetryAttempts {

        @Test
        @DisplayName("TC-BFR-03: fails first attempt then succeeds second → 2 recordStage calls total")
        void failsOnce_succeedsOnSecond() {
            doThrow(new RuntimeException("DB timeout"))
                    .doNothing()
                    .when(feedbackService)
                    .recordStage(anyLong(), anyString(), any(), anyBoolean(), any());
            when(feedbackService.isLoopComplete(2L)).thenReturn(false);

            retryService.recordStageWithRetry(2L, "B002", FeedbackStage.ACTION_TAKEN, true, null);

            verify(feedbackService, times(2))
                    .recordStage(2L, "B002", FeedbackStage.ACTION_TAKEN, true, null);
            verify(metrics).recordStageCompleted(FeedbackStage.ACTION_TAKEN);
            verify(kafkaTemplate, never()).send(any(), any(), any());
        }

        @Test
        @DisplayName("TC-BFR-04: all 3 attempts fail → DLQ topic published, metrics.dlq called")
        void allAttemptsFail_publishesToDlq() {
            doThrow(new RuntimeException("DB down"))
                    .when(feedbackService)
                    .recordStage(anyLong(), anyString(), any(), anyBoolean(), any());
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            retryService.recordStageWithRetry(3L, "B003", FeedbackStage.COMMAND_ACKNOWLEDGED, false, "error");

            verify(feedbackService, times(BmsFeedbackRetryService.MAX_ATTEMPTS))
                    .recordStage(3L, "B003", FeedbackStage.COMMAND_ACKNOWLEDGED, false, "error");
            verify(kafkaTemplate).send(
                    eq(BmsFeedbackRetryService.DLQ_TOPIC),
                    eq("3"),
                    any(String.class)
            );
            verify(metrics).recordDlq();
        }

        @Test
        @DisplayName("TC-BFR-05: DLQ payload contains commandId, stage, and error message")
        void dlqPayload_containsCommandIdAndStage() throws Exception {
            doThrow(new RuntimeException("DB unavailable"))
                    .when(feedbackService)
                    .recordStage(anyLong(), anyString(), any(), anyBoolean(), any());
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            retryService.recordStageWithRetry(5L, "B005", FeedbackStage.FEEDBACK_VERIFIED, true, null);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(any(), any(), payloadCaptor.capture());

            String payload = payloadCaptor.getValue();
            assertThat(payload).contains("\"commandId\":5");
            assertThat(payload).contains("FEEDBACK_VERIFIED");
            assertThat(payload).contains("B005");
            assertThat(payload).contains("DB unavailable");
        }
    }

    // ── Null/edge cases ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("TC-BFR-06: null commandId handled gracefully in DLQ routing")
        void nullCommandId_dlqPublishedWithSafeKey() {
            doThrow(new RuntimeException("fail"))
                    .when(feedbackService)
                    .recordStage(isNull(), anyString(), any(), anyBoolean(), any());
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            retryService.recordStageWithRetry(null, "B007", FeedbackStage.COMMAND_SENT, true, null);

            verify(kafkaTemplate).send(
                    eq(BmsFeedbackRetryService.DLQ_TOPIC),
                    eq("unknown"),
                    any(String.class)
            );
        }
    }
}
