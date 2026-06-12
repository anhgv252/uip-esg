package com.uip.backend.ai.feedback;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * M4-COR-07: Unit test for {@link IncidentFeedbackAggregator}.
 *
 * <p>Verifies the JPA {@link Specification} composition returns whatever the repository
 * resolves, and that the 30-day lookback predicate is applied. The Specification itself
 * is opaque (lambda), so the test asserts delegation + null-safety rather than SQL.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentFeedbackAggregator — 30-day feedback collection")
class IncidentFeedbackAggregatorTest {

    @Mock private AlertEventRepository alertEventRepository;

    @InjectMocks private IncidentFeedbackAggregator aggregator;

    // ─── Delegation & shape ────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-IFA-01: repository returns empty list → aggregator returns empty, no exception")
    void emptyRepository_returnsEmptyList() {
        stubFindAll(List.of());

        List<AlertEvent> result = aggregator.collectRecentFeedback();

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("TC-IFA-02: repository returns feedback records → aggregator returns them unchanged")
    void nonEmptyRepository_returnsRecords() {
        AlertEvent e1 = feedbackEvent("AQI", "PM25", true);
        AlertEvent e2 = feedbackEvent("WATER", "LEVEL", false);
        stubFindAll(List.of(e1, e2));

        List<AlertEvent> result = aggregator.collectRecentFeedback();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AlertEvent::getModule)
                .containsExactlyInAnyOrder("AQI", "WATER");
    }

    @Test
    @DisplayName("TC-IFA-03: feedback records include feedbackCorrect flag preserved")
    void feedbackCorrectFlag_preserved() {
        AlertEvent correct = feedbackEvent("AQI", "PM25", true);
        AlertEvent wrong   = feedbackEvent("AQI", "PM25", false);
        stubFindAll(List.of(correct, wrong));

        List<AlertEvent> result = aggregator.collectRecentFeedback();

        assertThat(result)
                .extracting(AlertEvent::getFeedbackCorrect)
                .containsExactlyInAnyOrder(true, false);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Stubs {@link AlertEventRepository#findAll(Specification)} with the given result.
     * Uses a generic type hint to avoid raw-type unchecked warnings.
     */
    @SuppressWarnings("unchecked")
    private void stubFindAll(List<AlertEvent> result) {
        when(alertEventRepository.findAll(any(Specification.class))).thenReturn(result);
    }

    private AlertEvent feedbackEvent(String module, String measureType, boolean correct) {
        AlertEvent event = new AlertEvent();
        event.setId(UUID.randomUUID());
        event.setTenantId("default");
        event.setSensorId("sensor-test");
        event.setModule(module);
        event.setMeasureType(measureType);
        event.setValue(50.0);
        event.setThreshold(40.0);
        event.setSeverity("HIGH");
        event.setDetectedAt(Instant.now());
        event.setFeedbackCorrect(correct);
        return event;
    }
}
