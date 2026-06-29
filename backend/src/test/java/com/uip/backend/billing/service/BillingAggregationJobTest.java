package com.uip.backend.billing.service;

import com.uip.backend.billing.domain.MeteringEvent;
import com.uip.backend.billing.domain.MeteringEventType;
import com.uip.backend.billing.domain.MonthlyUsage;
import com.uip.backend.billing.repository.MeteringEventRepository;
import com.uip.backend.billing.repository.MonthlyUsageRepository;
import com.uip.backend.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BillingAggregationJob (M5-4 T01).
 * 
 * Coverage:
 * - Aggregation logic per building
 * - Billing formula (base fee + AI overage)
 * - Idempotency (upsert behavior)
 * - Event type classification
 */
@ExtendWith(MockitoExtension.class)
class BillingAggregationJobTest {

    @Mock
    private MeteringEventRepository meteringEventRepository;

    @Mock
    private MonthlyUsageRepository monthlyUsageRepository;

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private BillingAggregationJob billingAggregationJob;

    private static final String TENANT_ID = "test-tenant";
    private static final String BUILDING_ID = "building-01";
    private static final String BILLING_MONTH = "2026-06";

    @BeforeEach
    void setUp() {
        when(tenantService.getAllTenantIds()).thenReturn(List.of(TENANT_ID));
    }

    @Test
    void testAggregateMonth_calculatesTotalsCorrectly() {
        // Given: 5 sensor readings, 10 AI inferences (150K tokens), 2 alerts, 3 workflows
        List<MeteringEvent> events = List.of(
                createEvent(MeteringEventType.SENSOR_READING, 0L),
                createEvent(MeteringEventType.SENSOR_READING, 0L),
                createEvent(MeteringEventType.SENSOR_READING, 0L),
                createEvent(MeteringEventType.SENSOR_READING, 0L),
                createEvent(MeteringEventType.SENSOR_READING, 0L),
                createEvent(MeteringEventType.AI_INFERENCE, 15_000L),
                createEvent(MeteringEventType.AI_INFERENCE, 15_000L),
                createEvent(MeteringEventType.AI_INFERENCE, 15_000L),
                createEvent(MeteringEventType.AI_INFERENCE, 15_000L),
                createEvent(MeteringEventType.AI_INFERENCE, 15_000L),
                createEvent(MeteringEventType.AI_PREDICTION, 15_000L),
                createEvent(MeteringEventType.AI_PREDICTION, 15_000L),
                createEvent(MeteringEventType.AI_PREDICTION, 15_000L),
                createEvent(MeteringEventType.AI_PREDICTION, 15_000L),
                createEvent(MeteringEventType.AI_PREDICTION, 15_000L),
                createEvent(MeteringEventType.ALERT_GENERATED, 0L),
                createEvent(MeteringEventType.ALERT_GENERATED, 0L),
                createEvent(MeteringEventType.BPMN_WORKFLOW_EXECUTED, 0L),
                createEvent(MeteringEventType.BPMN_WORKFLOW_EXECUTED, 0L),
                createEvent(MeteringEventType.BPMN_WORKFLOW_EXECUTED, 0L)
        );

        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(events);
        when(monthlyUsageRepository.findByTenantIdAndBuildingIdAndBillingMonth(TENANT_ID, BUILDING_ID, BILLING_MONTH))
                .thenReturn(Optional.empty());

        // When
        billingAggregationJob.aggregateMonth(BILLING_MONTH);

        // Then
        ArgumentCaptor<MonthlyUsage> captor = ArgumentCaptor.forClass(MonthlyUsage.class);
        verify(monthlyUsageRepository).save(captor.capture());

        MonthlyUsage saved = captor.getValue();
        assertThat(saved.getTotalSensorReadings()).isEqualTo(5);
        assertThat(saved.getTotalAiInferences()).isEqualTo(10);
        assertThat(saved.getTotalAiTokens()).isEqualTo(150_000L);
        assertThat(saved.getTotalAlerts()).isEqualTo(2);
        assertThat(saved.getTotalWorkflowExecutions()).isEqualTo(3);
    }

    @Test
    void testAggregateMonth_appliesBaseFee() {
        // Given: minimal events
        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(createEvent(MeteringEventType.SENSOR_READING, 0L)));
        when(monthlyUsageRepository.findByTenantIdAndBuildingIdAndBillingMonth(TENANT_ID, BUILDING_ID, BILLING_MONTH))
                .thenReturn(Optional.empty());

        // When
        billingAggregationJob.aggregateMonth(BILLING_MONTH);

        // Then
        ArgumentCaptor<MonthlyUsage> captor = ArgumentCaptor.forClass(MonthlyUsage.class);
        verify(monthlyUsageRepository).save(captor.capture());

        MonthlyUsage saved = captor.getValue();
        assertThat(saved.getBaseFeeVnd()).isEqualTo(2_000_000L);  // 2M VND base
    }

    @Test
    void testAggregateMonth_calculatesAiOverage_whenAboveBaseline() {
        // Given: 150K tokens (50K over 100K baseline) = 50/1 * 50 = 2500 VND overage
        List<MeteringEvent> events = List.of(
                createEvent(MeteringEventType.AI_INFERENCE, 150_000L)
        );

        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(events);
        when(monthlyUsageRepository.findByTenantIdAndBuildingIdAndBillingMonth(TENANT_ID, BUILDING_ID, BILLING_MONTH))
                .thenReturn(Optional.empty());

        // When
        billingAggregationJob.aggregateMonth(BILLING_MONTH);

        // Then
        ArgumentCaptor<MonthlyUsage> captor = ArgumentCaptor.forClass(MonthlyUsage.class);
        verify(monthlyUsageRepository).save(captor.capture());

        MonthlyUsage saved = captor.getValue();
        assertThat(saved.getAiOverageVnd()).isEqualTo(2_500L);  // 50K overage / 1K * 50 VND
        assertThat(saved.getTotalCostVnd()).isEqualTo(2_002_500L);  // base + overage
    }

    @Test
    void testAggregateMonth_noOverage_whenBelowBaseline() {
        // Given: 50K tokens (below 100K baseline)
        List<MeteringEvent> events = List.of(
                createEvent(MeteringEventType.AI_INFERENCE, 50_000L)
        );

        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(events);
        when(monthlyUsageRepository.findByTenantIdAndBuildingIdAndBillingMonth(TENANT_ID, BUILDING_ID, BILLING_MONTH))
                .thenReturn(Optional.empty());

        // When
        billingAggregationJob.aggregateMonth(BILLING_MONTH);

        // Then
        ArgumentCaptor<MonthlyUsage> captor = ArgumentCaptor.forClass(MonthlyUsage.class);
        verify(monthlyUsageRepository).save(captor.capture());

        MonthlyUsage saved = captor.getValue();
        assertThat(saved.getAiOverageVnd()).isEqualTo(0L);
        assertThat(saved.getTotalCostVnd()).isEqualTo(2_000_000L);  // base only
    }

    @Test
    void testAggregateMonth_idempotency_updatesExistingRecord() {
        // Given: existing monthly_usage record
        MonthlyUsage existing = MonthlyUsage.builder()
                .id(123L)
                .tenantId(TENANT_ID)
                .buildingId(BUILDING_ID)
                .billingMonth(BILLING_MONTH)
                .totalSensorReadings(10L)
                .build();

        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(createEvent(MeteringEventType.SENSOR_READING, 0L)));
        when(monthlyUsageRepository.findByTenantIdAndBuildingIdAndBillingMonth(TENANT_ID, BUILDING_ID, BILLING_MONTH))
                .thenReturn(Optional.of(existing));

        // When
        billingAggregationJob.aggregateMonth(BILLING_MONTH);

        // Then: existing record is updated, not duplicated
        ArgumentCaptor<MonthlyUsage> captor = ArgumentCaptor.forClass(MonthlyUsage.class);
        verify(monthlyUsageRepository).save(captor.capture());

        MonthlyUsage saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(123L);  // Same ID = update
        assertThat(saved.getTotalSensorReadings()).isEqualTo(1L);  // New count
    }

    private MeteringEvent createEvent(MeteringEventType type, Long tokenCount) {
        return MeteringEvent.builder()
                .tenantId(TENANT_ID)
                .buildingId(BUILDING_ID)
                .eventType(type)
                .tokenCount(tokenCount)
                .recordedAt(Instant.now())
                .build();
    }
}
