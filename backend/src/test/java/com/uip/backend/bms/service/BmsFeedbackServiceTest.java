package com.uip.backend.bms.service;

import com.uip.backend.bms.domain.BmsFeedbackEvent;
import com.uip.backend.bms.domain.FeedbackStage;
import com.uip.backend.bms.repository.BmsFeedbackEventRepository;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BmsFeedbackService — unit")
class BmsFeedbackServiceTest {

    @Mock private BmsFeedbackEventRepository feedbackEventRepository;

    @InjectMocks private BmsFeedbackService service;

    // ── recordStage ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordStage")
    class RecordStage {

        @Test
        @DisplayName("saves feedback event with correct fields")
        void savesEventWithCorrectFields() {
            when(feedbackEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordStage(42L, FeedbackStage.COMMAND_SENT, true, null);

            ArgumentCaptor<BmsFeedbackEvent> captor =
                    ArgumentCaptor.forClass(BmsFeedbackEvent.class);
            verify(feedbackEventRepository).save(captor.capture());

            BmsFeedbackEvent saved = captor.getValue();
            assertThat(saved.getPendingCommandId()).isEqualTo(42L);
            assertThat(saved.getStage()).isEqualTo(FeedbackStage.COMMAND_SENT);
            assertThat(saved.isSuccess()).isTrue();
            assertThat(saved.getNotes()).isNull();
        }

        @Test
        @DisplayName("saves event with buildingId when overload used")
        void savesEventWithBuildingId() {
            when(feedbackEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordStage(42L, "B001", FeedbackStage.ACTION_TAKEN, true, "Sprinkler activated");

            ArgumentCaptor<BmsFeedbackEvent> captor =
                    ArgumentCaptor.forClass(BmsFeedbackEvent.class);
            verify(feedbackEventRepository).save(captor.capture());

            BmsFeedbackEvent saved = captor.getValue();
            assertThat(saved.getBuildingId()).isEqualTo("B001");
            assertThat(saved.getStage()).isEqualTo(FeedbackStage.ACTION_TAKEN);
            assertThat(saved.getNotes()).isEqualTo("Sprinkler activated");
        }
    }

    // ── getFeedbackTimeline ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getFeedbackTimeline")
    class GetFeedbackTimeline {

        @Test
        @DisplayName("returns events ordered by recordedAt ascending")
        void returnsOrderedEvents() {
            Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
            Instant t2 = Instant.parse("2026-01-01T10:00:05Z");
            Instant t3 = Instant.parse("2026-01-01T10:00:10Z");

            BmsFeedbackEvent e1 = feedbackEvent(100L, FeedbackStage.COMMAND_SENT, true, t1);
            BmsFeedbackEvent e2 = feedbackEvent(100L, FeedbackStage.COMMAND_ACKNOWLEDGED, true, t2);
            BmsFeedbackEvent e3 = feedbackEvent(100L, FeedbackStage.ACTION_TAKEN, true, t3);

            when(feedbackEventRepository.findByPendingCommandIdOrderByRecordedAtAsc(100L))
                    .thenReturn(List.of(e1, e2, e3));

            List<BmsFeedbackEvent> timeline = service.getFeedbackTimeline(100L);

            assertThat(timeline).hasSize(3);
            assertThat(timeline.get(0).getStage()).isEqualTo(FeedbackStage.COMMAND_SENT);
            assertThat(timeline.get(1).getStage()).isEqualTo(FeedbackStage.COMMAND_ACKNOWLEDGED);
            assertThat(timeline.get(2).getStage()).isEqualTo(FeedbackStage.ACTION_TAKEN);
        }
    }

    // ── isLoopComplete ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isLoopComplete")
    class IsLoopComplete {

        @Test
        @DisplayName("returns true when all four stages have success=true")
        void allFourStagesSuccess_returnsTrue() {
            stubStage(100L, FeedbackStage.COMMAND_SENT,         List.of(successEvent(FeedbackStage.COMMAND_SENT)));
            stubStage(100L, FeedbackStage.COMMAND_ACKNOWLEDGED, List.of(successEvent(FeedbackStage.COMMAND_ACKNOWLEDGED)));
            stubStage(100L, FeedbackStage.ACTION_TAKEN,         List.of(successEvent(FeedbackStage.ACTION_TAKEN)));
            stubStage(100L, FeedbackStage.FEEDBACK_VERIFIED,    List.of(successEvent(FeedbackStage.FEEDBACK_VERIFIED)));

            assertThat(service.isLoopComplete(100L)).isTrue();
        }

        @Test
        @DisplayName("returns false when FEEDBACK_VERIFIED stage is missing")
        void missingFeedbackVerified_returnsFalse() {
            stubStage(100L, FeedbackStage.COMMAND_SENT,         List.of(successEvent(FeedbackStage.COMMAND_SENT)));
            stubStage(100L, FeedbackStage.COMMAND_ACKNOWLEDGED, List.of(successEvent(FeedbackStage.COMMAND_ACKNOWLEDGED)));
            stubStage(100L, FeedbackStage.ACTION_TAKEN,         List.of(successEvent(FeedbackStage.ACTION_TAKEN)));
            stubStage(100L, FeedbackStage.FEEDBACK_VERIFIED,    List.of());   // missing

            assertThat(service.isLoopComplete(100L)).isFalse();
        }

        @Test
        @DisplayName("returns false when one stage has success=false")
        void oneStageFailure_returnsFalse() {
            stubStage(100L, FeedbackStage.COMMAND_SENT,         List.of(successEvent(FeedbackStage.COMMAND_SENT)));
            stubStage(100L, FeedbackStage.COMMAND_ACKNOWLEDGED, List.of(failedEvent(FeedbackStage.COMMAND_ACKNOWLEDGED)));
            stubStage(100L, FeedbackStage.ACTION_TAKEN,         List.of(successEvent(FeedbackStage.ACTION_TAKEN)));
            stubStage(100L, FeedbackStage.FEEDBACK_VERIFIED,    List.of(successEvent(FeedbackStage.FEEDBACK_VERIFIED)));

            assertThat(service.isLoopComplete(100L)).isFalse();
        }

        @Test
        @DisplayName("returns false when no stages recorded at all")
        void noStagesRecorded_returnsFalse() {
            for (FeedbackStage stage : FeedbackStage.values()) {
                stubStage(100L, stage, List.of());
            }

            assertThat(service.isLoopComplete(100L)).isFalse();
        }

        @Test
        @DisplayName("handles multiple events per stage — returns true if any is successful")
        void multipleEventsPerStage_trueIfAnySuccess() {
            // COMMAND_SENT: first failed, second succeeded (retry scenario)
            stubStage(100L, FeedbackStage.COMMAND_SENT,
                    List.of(failedEvent(FeedbackStage.COMMAND_SENT),
                            successEvent(FeedbackStage.COMMAND_SENT)));
            stubStage(100L, FeedbackStage.COMMAND_ACKNOWLEDGED, List.of(successEvent(FeedbackStage.COMMAND_ACKNOWLEDGED)));
            stubStage(100L, FeedbackStage.ACTION_TAKEN,         List.of(successEvent(FeedbackStage.ACTION_TAKEN)));
            stubStage(100L, FeedbackStage.FEEDBACK_VERIFIED,    List.of(successEvent(FeedbackStage.FEEDBACK_VERIFIED)));

            assertThat(service.isLoopComplete(100L)).isTrue();
        }
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    private BmsFeedbackEvent feedbackEvent(Long commandId, FeedbackStage stage,
                                            boolean success, Instant recordedAt) {
        BmsFeedbackEvent e = new BmsFeedbackEvent();
        e.setPendingCommandId(commandId);
        e.setStage(stage);
        e.setSuccess(success);
        e.setRecordedAt(recordedAt);
        return e;
    }

    private BmsFeedbackEvent successEvent(FeedbackStage stage) {
        return feedbackEvent(100L, stage, true, Instant.now());
    }

    private BmsFeedbackEvent failedEvent(FeedbackStage stage) {
        return feedbackEvent(100L, stage, false, Instant.now());
    }

    private void stubStage(Long commandId, FeedbackStage stage, List<BmsFeedbackEvent> events) {
        when(feedbackEventRepository.findByPendingCommandIdAndStage(commandId, stage))
                .thenReturn(events);
    }
}
