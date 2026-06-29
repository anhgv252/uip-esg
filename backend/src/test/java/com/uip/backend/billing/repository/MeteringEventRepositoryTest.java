package com.uip.backend.billing.repository;

import com.uip.backend.billing.domain.MeteringEvent;
import com.uip.backend.billing.domain.MeteringEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MeteringEventRepository.
 * Uses @DataJpaTest for in-memory H2 database.
 */
@DataJpaTest
@ActiveProfiles("test")
class MeteringEventRepositoryTest {

    @Autowired
    private MeteringEventRepository repository;

    private String tenantId;
    private Instant now;
    private Instant oneDayAgo;
    private Instant oneHourAgo;

    @BeforeEach
    void setUp() {
        tenantId = "test-tenant-001";
        now = Instant.now();
        oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        repository.deleteAll();
    }

    @Test
    void testSaveAndFindByTenantAndTimeRange() {
        // Given
        MeteringEvent event1 = createMeteringEvent("evt-001", MeteringEventType.AI_PREDICTION, 100, oneHourAgo);
        MeteringEvent event2 = createMeteringEvent("evt-002", MeteringEventType.WORKFLOW_RUN, 200, now);
        repository.save(event1);
        repository.save(event2);

        // When
        List<MeteringEvent> events = repository.findByTenantAndTimeRange(tenantId, oneDayAgo, now.plusSeconds(1));

        // Then
        assertThat(events).hasSize(2);
        assertThat(events).extracting(MeteringEvent::getEventId).containsExactlyInAnyOrder("evt-001", "evt-002");
    }

    @Test
    void testFindByTenantTypeAndTimeRange() {
        // Given
        MeteringEvent event1 = createMeteringEvent("evt-001", MeteringEventType.AI_PREDICTION, 100, oneHourAgo);
        MeteringEvent event2 = createMeteringEvent("evt-002", MeteringEventType.WORKFLOW_RUN, 200, now);
        MeteringEvent event3 = createMeteringEvent("evt-003", MeteringEventType.AI_PREDICTION, 150, now);
        repository.save(event1);
        repository.save(event2);
        repository.save(event3);

        // When
        List<MeteringEvent> aiEvents = repository.findByTenantTypeAndTimeRange(
                tenantId, MeteringEventType.AI_PREDICTION, oneDayAgo, now.plusSeconds(1));

        // Then
        assertThat(aiEvents).hasSize(2);
        assertThat(aiEvents).extracting(MeteringEvent::getEventType)
                .containsOnly(MeteringEventType.AI_PREDICTION);
    }

    @Test
    void testSumCostByTenantAndTimeRange() {
        // Given
        MeteringEvent event1 = createMeteringEvent("evt-001", MeteringEventType.AI_PREDICTION, 100, oneHourAgo);
        MeteringEvent event2 = createMeteringEvent("evt-002", MeteringEventType.WORKFLOW_RUN, 200, now);
        MeteringEvent event3 = createMeteringEvent("evt-003", MeteringEventType.API_CALL, 50, now);
        repository.save(event1);
        repository.save(event2);
        repository.save(event3);

        // When
        Long totalCost = repository.sumCostByTenantAndTimeRange(tenantId, oneDayAgo, now.plusSeconds(1));

        // Then
        assertThat(totalCost).isEqualTo(350L); // 100 + 200 + 50
    }

    @Test
    void testSumCostByTenantAndTimeRange_EmptyResult() {
        // When
        Long totalCost = repository.sumCostByTenantAndTimeRange(tenantId, oneDayAgo, now.plusSeconds(1));

        // Then
        assertThat(totalCost).isEqualTo(0L); // COALESCE(SUM(...), 0)
    }

    @Test
    void testExistsByEventId() {
        // Given
        MeteringEvent event = createMeteringEvent("evt-001", MeteringEventType.AI_PREDICTION, 100, now);
        repository.save(event);

        // When & Then
        assertThat(repository.existsByEventId("evt-001")).isTrue();
        assertThat(repository.existsByEventId("evt-999")).isFalse();
    }

    @Test
    void testFindByTenantAndTimeRange_OutsideTimeWindow() {
        // Given
        MeteringEvent event = createMeteringEvent("evt-001", MeteringEventType.AI_PREDICTION, 100, now);
        repository.save(event);

        // When
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        List<MeteringEvent> events = repository.findByTenantAndTimeRange(tenantId, twoDaysAgo, oneDayAgo);

        // Then
        assertThat(events).isEmpty();
    }

    private MeteringEvent createMeteringEvent(String eventId, MeteringEventType eventType, int costCents, Instant recordedAt) {
        MeteringEvent event = new MeteringEvent();
        event.setTenantId(tenantId);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setCostUsdCents(costCents);
        event.setRecordedAt(recordedAt);
        return event;
    }
}
