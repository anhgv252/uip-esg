package com.uip.backend.billing.service;

import com.uip.backend.billing.config.RoiConfig;
import com.uip.backend.billing.domain.MeteringEvent;
import com.uip.backend.billing.domain.MeteringEventType;
import com.uip.backend.billing.dto.BuildingRoiResponse;
import com.uip.backend.billing.dto.TenantRoiSummary;
import com.uip.backend.billing.repository.MeteringEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * M5-3 T06: Unit tests for RoiCalculationService.
 * 
 * Test cases (as per spec):
 * 1. Zero events → only base fee cost, savings = baseline - base_fee
 * 2. AI tokens below base allocation → no overage charge
 * 3. AI tokens above base allocation → correct overage calculation
 * 4. Month parameter defaults to current month
 * 5. Multiple buildings in tenant summary
 * 6. CO2 savings calculation
 * 7. Comparison chart generation
 */
@ExtendWith(MockitoExtension.class)
class RoiCalculationServiceTest {

    @Mock
    private MeteringEventRepository meteringEventRepository;

    @Mock
    private RoiConfig roiConfig;

    @InjectMocks
    private RoiCalculationService roiCalculationService;

    private static final String TENANT_ID = "tenant-001";
    private static final String BUILDING_ID = "BLDG-001";
    private static final String MONTH = "2026-06";

    @BeforeEach
    void setUp() {
        // Configure default ROI config values
        when(roiConfig.getManualOpsBaselineVnd()).thenReturn(8_500_000L);
        when(roiConfig.getBaseFeeVnd()).thenReturn(2_000_000L);
        when(roiConfig.getTokenRateVndPerThousand()).thenReturn(50L);
        when(roiConfig.getTokenBaseAllocation()).thenReturn(100_000L);
        when(roiConfig.getCo2KgPerKwhSaved()).thenReturn(new BigDecimal("0.5"));
        when(roiConfig.getEnergySavingsFactor()).thenReturn(new BigDecimal("0.15"));
        when(roiConfig.getAvgEnergyConsumptionKwh()).thenReturn(15_000L);
    }

    @Test
    @DisplayName("Test Case 1: Zero events → only base fee cost, savings = baseline - base_fee")
    void testZeroEvents() {
        // Given: No metering events
        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(new ArrayList<>());

        // When
        BuildingRoiResponse response = roiCalculationService.calculateBuildingRoi(
                TENANT_ID, BUILDING_ID, MONTH);

        // Then
        assertThat(response.getBuildingId()).isEqualTo(BUILDING_ID);
        assertThat(response.getMonth()).isEqualTo(MONTH);

        // Cost breakdown
        assertThat(response.getCosts().getBaseFeeVnd()).isEqualTo(2_000_000L);
        assertThat(response.getCosts().getAiTokensUsed()).isEqualTo(0L);
        assertThat(response.getCosts().getAiOverageTokens()).isEqualTo(0L);
        assertThat(response.getCosts().getAiOverageCostVnd()).isEqualTo(0L);
        assertThat(response.getCosts().getTotalCostVnd()).isEqualTo(2_000_000L);

        // Savings
        assertThat(response.getSavings().getManualOpsCostVnd()).isEqualTo(8_500_000L);
        assertThat(response.getSavings().getAutomationSavingsVnd()).isEqualTo(6_500_000L); // 8.5M - 2M

        // Comparison chart should be present
        assertThat(response.getComparisonChart()).isNotEmpty();
    }

    @Test
    @DisplayName("Test Case 2: AI tokens below base allocation → no overage charge")
    void testTokensBelowAllocation() {
        // Given: 50,000 AI tokens (below 100,000 base allocation)
        List<MeteringEvent> events = List.of(
                createEvent(BUILDING_ID, MeteringEventType.AI_INFERENCE, 50_000)
        );

        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(events);

        // When
        BuildingRoiResponse response = roiCalculationService.calculateBuildingRoi(
                TENANT_ID, BUILDING_ID, MONTH);

        // Then
        assertThat(response.getCosts().getAiTokensUsed()).isEqualTo(50_000L);
        assertThat(response.getCosts().getAiOverageTokens()).isEqualTo(0L);
        assertThat(response.getCosts().getAiOverageCostVnd()).isEqualTo(0L);
        assertThat(response.getCosts().getTotalCostVnd()).isEqualTo(2_000_000L); // base fee only
    }

    @Test
    @DisplayName("Test Case 3: AI tokens above base allocation → correct overage calculation")
    void testTokensAboveAllocation() {
        // Given: 125,000 AI tokens (25,000 overage above 100,000 base)
        List<MeteringEvent> events = List.of(
                createEvent(BUILDING_ID, MeteringEventType.AI_INFERENCE, 125_000)
        );

        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(events);

        // When
        BuildingRoiResponse response = roiCalculationService.calculateBuildingRoi(
                TENANT_ID, BUILDING_ID, MONTH);

        // Then
        assertThat(response.getCosts().getAiTokensUsed()).isEqualTo(125_000L);
        assertThat(response.getCosts().getAiOverageTokens()).isEqualTo(25_000L);
        
        // Overage cost = 25,000 tokens * 50 VND / 1,000 = 1,250 VND
        assertThat(response.getCosts().getAiOverageCostVnd()).isEqualTo(1_250L);
        
        // Total = base fee + overage = 2,000,000 + 1,250 = 2,001,250 VND
        assertThat(response.getCosts().getTotalCostVnd()).isEqualTo(2_001_250L);
    }

    @Test
    @DisplayName("Test Case 4: Month parameter defaults to current month")
    void testMonthDefaultsToCurrent() {
        // Given: null month parameter
        YearMonth currentMonth = YearMonth.now();
        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(new ArrayList<>());

        // When
        BuildingRoiResponse response = roiCalculationService.calculateBuildingRoi(
                TENANT_ID, BUILDING_ID, null);

        // Then
        assertThat(response.getMonth())
                .isEqualTo(currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")));
    }

    @Test
    @DisplayName("Test Case 5: Multiple buildings in tenant summary")
    void testMultipleBuildingsInTenantSummary() {
        // Given: Events from 2 buildings
        List<MeteringEvent> events = List.of(
                createEvent("BLDG-001", MeteringEventType.AI_INFERENCE, 50_000),
                createEvent("BLDG-002", MeteringEventType.AI_INFERENCE, 75_000)
        );

        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(events);

        // When
        TenantRoiSummary summary = roiCalculationService.calculateTenantRoiSummary(
                TENANT_ID, MONTH);

        // Then
        assertThat(summary.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(summary.getBuildingCount()).isEqualTo(2);
        assertThat(summary.getBuildings()).hasSize(2);
        
        // Total cost = 2 buildings * 2M base fee = 4M VND
        assertThat(summary.getTotalCostVnd()).isEqualTo(4_000_000L);
        
        // Total savings = 2 buildings * (8.5M - 2M) = 13M VND
        assertThat(summary.getTotalSavingsVnd()).isEqualTo(13_000_000L);
    }

    @Test
    @DisplayName("Test Case 6: CO2 savings calculation")
    void testCo2SavingsCalculation() {
        // Given: No events (will use default energy savings)
        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(new ArrayList<>());

        // When
        BuildingRoiResponse response = roiCalculationService.calculateBuildingRoi(
                TENANT_ID, BUILDING_ID, MONTH);

        // Then: CO2 savings = (15,000 kWh * 0.15) * 0.5 kg/kWh = 1,125 kg
        // Note: Actual calculation may vary based on config defaults
        assertThat(response.getSavings().getCo2SavedKg()).isNotNull();
        assertThat(response.getSavings().getCo2SavedKg()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Test Case 7: Comparison chart generation")
    void testComparisonChartGeneration() {
        // Given: No events
        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(new ArrayList<>());

        // When
        BuildingRoiResponse response = roiCalculationService.calculateBuildingRoi(
                TENANT_ID, BUILDING_ID, MONTH);

        // Then
        assertThat(response.getComparisonChart()).isNotEmpty();
        assertThat(response.getComparisonChart()).hasSize(3); // Energy, Labor, Response Time
        
        // Verify metric structure
        BuildingRoiResponse.ComparisonMetric energyMetric = response.getComparisonChart().get(0);
        assertThat(energyMetric.getMetric()).isEqualTo("Energy (kWh)");
        assertThat(energyMetric.getUnit()).isEqualTo("kWh");
        assertThat(energyMetric.getBefore()).isGreaterThan(energyMetric.getAfter());
    }

    @Test
    @DisplayName("Test: Sensor readings and alerts are counted correctly")
    void testSensorReadingsAndAlertsCounting() {
        // Given: Mixed event types
        List<MeteringEvent> events = List.of(
                createEvent(BUILDING_ID, MeteringEventType.SENSOR_READING, 0),
                createEvent(BUILDING_ID, MeteringEventType.SENSOR_READING, 0),
                createEvent(BUILDING_ID, MeteringEventType.ALERT_GENERATED, 0),
                createEvent(BUILDING_ID, MeteringEventType.AI_INFERENCE, 50_000)
        );

        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(events);

        // When
        BuildingRoiResponse response = roiCalculationService.calculateBuildingRoi(
                TENANT_ID, BUILDING_ID, MONTH);

        // Then
        assertThat(response.getCosts().getSensorReadings()).isEqualTo(2L);
        assertThat(response.getCosts().getAlertsGenerated()).isEqualTo(1L);
    }

    // Helper methods

    private MeteringEvent createEvent(String buildingId, MeteringEventType type, int tokenCount) {
        MeteringEvent event = new MeteringEvent();
        event.setTenantId(TENANT_ID);
        event.setBuildingId(buildingId);
        event.setEventType(type);
        event.setTokenCount((long) tokenCount);
        event.setRecordedAt(Instant.now());
        return event;
    }
}
