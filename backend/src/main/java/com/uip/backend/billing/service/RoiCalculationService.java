package com.uip.backend.billing.service;

import com.uip.backend.billing.config.RoiConfig;
import com.uip.backend.billing.domain.MeteringEvent;
import com.uip.backend.billing.domain.MeteringEventType;
import com.uip.backend.billing.dto.BuildingRoiResponse;
import com.uip.backend.billing.dto.TenantRoiSummary;
import com.uip.backend.billing.repository.MeteringEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M5-3 T06: ROI calculation service for building-level and tenant-level ROI.
 * 
 * Billing model (D4 Decision):
 * - Base fee: 2,000,000 VND/building/month
 * - AI tokens: 100,000 base allocation, 50 VND per 1,000 tokens overage
 * - Manual ops baseline: 8,500,000 VND/building/month
 * - ROI formula: Annual savings = (Manual ops cost) - (UIP subscription + AI cost)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoiCalculationService {

    private final MeteringEventRepository meteringEventRepository;
    private final RoiConfig roiConfig;

    /**
     * Calculate ROI for a single building in a specific month.
     * 
     * @param tenantId Tenant identifier
     * @param buildingId Building identifier
     * @param month Month in YYYY-MM format (null = current month)
     * @return Building ROI response with cost breakdown and savings
     */
    @Transactional(readOnly = true)
    public BuildingRoiResponse calculateBuildingRoi(String tenantId, String buildingId, String month) {
        // Parse month or default to current
        YearMonth targetMonth = (month != null && !month.isBlank())
                ? YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"))
                : YearMonth.now();

        log.info("Calculating ROI for building {} (tenant {}) in month {}", buildingId, tenantId, targetMonth);

        // Query metering events for this building + month
        Instant monthStart = targetMonth.atDay(1).atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant monthEnd = targetMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.of("UTC")).toInstant();

        List<MeteringEvent> events = meteringEventRepository.findByTenantAndTimeRange(
                tenantId, monthStart, monthEnd)
                .stream()
                .filter(e -> buildingId.equals(e.getBuildingId()))
                .collect(Collectors.toList());

        log.debug("Found {} metering events for building {} in {}", events.size(), buildingId, targetMonth);

        // Calculate costs
        BuildingRoiResponse.CostBreakdown costs = calculateCosts(events);

        // Calculate savings
        BuildingRoiResponse.SavingsBreakdown savings = calculateSavings(costs.getTotalCostVnd());

        // Generate comparison chart
        List<BuildingRoiResponse.ComparisonMetric> comparisonChart = generateComparisonChart();

        return BuildingRoiResponse.builder()
                .buildingId(buildingId)
                .month(targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .costs(costs)
                .savings(savings)
                .comparisonChart(comparisonChart)
                .build();
    }

    /**
     * Calculate tenant-level ROI summary (aggregate of all buildings).
     * 
     * @param tenantId Tenant identifier
     * @param month Month in YYYY-MM format (null = current month)
     * @return Tenant ROI summary
     */
    @Transactional(readOnly = true)
    public TenantRoiSummary calculateTenantRoiSummary(String tenantId, String month) {
        YearMonth targetMonth = (month != null && !month.isBlank())
                ? YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"))
                : YearMonth.now();

        log.info("Calculating tenant ROI summary for tenant {} in month {}", tenantId, targetMonth);

        // Query all events for tenant in this month
        Instant monthStart = targetMonth.atDay(1).atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant monthEnd = targetMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.of("UTC")).toInstant();

        List<MeteringEvent> allEvents = meteringEventRepository.findByTenantAndTimeRange(
                tenantId, monthStart, monthEnd);

        // Group by building
        Map<String, List<MeteringEvent>> eventsByBuilding = allEvents.stream()
                .filter(e -> e.getBuildingId() != null && !e.getBuildingId().isBlank())
                .collect(Collectors.groupingBy(MeteringEvent::getBuildingId));

        log.debug("Found {} buildings with metering events for tenant {}", eventsByBuilding.size(), tenantId);

        // Calculate per-building ROI
        List<BuildingRoiResponse> buildingRois = eventsByBuilding.entrySet().stream()
                .map(entry -> {
                    String bldgId = entry.getKey();
                    List<MeteringEvent> bldgEvents = entry.getValue();
                    
                    BuildingRoiResponse.CostBreakdown costs = calculateCosts(bldgEvents);
                    BuildingRoiResponse.SavingsBreakdown savings = calculateSavings(costs.getTotalCostVnd());

                    return BuildingRoiResponse.builder()
                            .buildingId(bldgId)
                            .month(targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                            .costs(costs)
                            .savings(savings)
                            .comparisonChart(generateComparisonChart())
                            .build();
                })
                .collect(Collectors.toList());

        // Aggregate totals
        long totalCost = buildingRois.stream()
                .mapToLong(r -> r.getCosts().getTotalCostVnd())
                .sum();

        long totalSavings = buildingRois.stream()
                .mapToLong(r -> r.getSavings().getAutomationSavingsVnd())
                .sum();

        BigDecimal avgPayback = buildingRois.isEmpty() ? BigDecimal.ZERO :
                buildingRois.stream()
                        .map(r -> r.getSavings().getPaybackMonths())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(buildingRois.size()), 2, RoundingMode.HALF_UP);

        BigDecimal totalCo2 = buildingRois.stream()
                .map(r -> r.getSavings().getCo2SavedKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TenantRoiSummary.builder()
                .tenantId(tenantId)
                .month(targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .buildingCount(buildingRois.size())
                .totalCostVnd(totalCost)
                .totalSavingsVnd(totalSavings)
                .avgPaybackMonths(avgPayback)
                .totalCo2SavedKg(totalCo2)
                .buildings(buildingRois)
                .build();
    }

    /**
     * Calculate cost breakdown from metering events.
     */
    private BuildingRoiResponse.CostBreakdown calculateCosts(List<MeteringEvent> events) {
        // Count AI tokens
        long aiTokensUsed = events.stream()
                .filter(e -> e.getEventType() == MeteringEventType.AI_INFERENCE)
                .mapToLong(e -> e.getTokenCount() != null ? e.getTokenCount() : 0)
                .sum();

        // Calculate AI overage
        long aiOverageTokens = Math.max(0, aiTokensUsed - roiConfig.getTokenBaseAllocation());
        long aiOverageCostVnd = (aiOverageTokens * roiConfig.getTokenRateVndPerThousand()) / 1000;

        // Total cost = base fee + AI overage
        long totalCostVnd = roiConfig.getBaseFeeVnd() + aiOverageCostVnd;

        // Count sensor readings and alerts for analytics
        long sensorReadings = events.stream()
                .filter(e -> e.getEventType() == MeteringEventType.SENSOR_READING)
                .count();

        long alertsGenerated = events.stream()
                .filter(e -> e.getEventType() == MeteringEventType.ALERT_GENERATED)
                .count();

        return BuildingRoiResponse.CostBreakdown.builder()
                .baseFeeVnd(roiConfig.getBaseFeeVnd())
                .aiTokensUsed(aiTokensUsed)
                .aiOverageTokens(aiOverageTokens)
                .aiOverageCostVnd(aiOverageCostVnd)
                .totalCostVnd(totalCostVnd)
                .sensorReadings(sensorReadings)
                .alertsGenerated(alertsGenerated)
                .build();
    }

    /**
     * Calculate savings breakdown comparing manual ops vs UIP.
     */
    private BuildingRoiResponse.SavingsBreakdown calculateSavings(long totalCostVnd) {
        long manualOpsCost = roiConfig.getManualOpsBaselineVnd();
        long automationSavings = manualOpsCost - totalCostVnd;

        // Payback months = (Initial investment) / (Monthly savings)
        // Assuming zero initial investment for SaaS model, payback is immediate if savings > 0
        // For more realistic case: assume initial setup cost = 3 months of base fee
        long initialInvestment = roiConfig.getBaseFeeVnd() * 3;
        BigDecimal paybackMonths = automationSavings > 0
                ? BigDecimal.valueOf(initialInvestment).divide(BigDecimal.valueOf(automationSavings), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // CO2 savings from energy reduction
        // Assume UIP automation reduces energy consumption by 15% (configurable)
        long energySavedKwh = roiConfig.getAvgEnergyConsumptionKwh()
                .multiply(roiConfig.getEnergySavingsFactor())
                .longValue();
        
        BigDecimal co2SavedKg = BigDecimal.valueOf(energySavedKwh)
                .multiply(roiConfig.getCo2KgPerKwhSaved());

        return BuildingRoiResponse.SavingsBreakdown.builder()
                .manualOpsCostVnd(manualOpsCost)
                .automationSavingsVnd(automationSavings)
                .paybackMonths(paybackMonths)
                .co2SavedKg(co2SavedKg)
                .build();
    }

    /**
     * Generate comparison chart (before vs after UIP).
     * 
     * Note: Uses default/estimated values for demo.
     * Production should fetch actual pre-UIP baseline data from pilot buildings.
     */
    private List<BuildingRoiResponse.ComparisonMetric> generateComparisonChart() {
        // Before UIP (manual monitoring)
        long energyBefore = roiConfig.getAvgEnergyConsumptionKwh();
        long energyAfter = energyBefore - (energyBefore * roiConfig.getEnergySavingsFactor().longValue() / 100);

        // Manual labor hours (estimated)
        long laborBefore = 160; // 160 hours/month
        long laborAfter = 40;   // 40 hours/month (75% reduction)

        // Incident response time (minutes)
        long responseTimeBefore = 45; // 45 minutes average
        long responseTimeAfter = 5;   // 5 minutes with automation

        return List.of(
                BuildingRoiResponse.ComparisonMetric.builder()
                        .metric("Energy (kWh)")
                        .before(energyBefore)
                        .after(energyAfter)
                        .unit("kWh")
                        .build(),
                BuildingRoiResponse.ComparisonMetric.builder()
                        .metric("Manual Labor")
                        .before(laborBefore)
                        .after(laborAfter)
                        .unit("hours")
                        .build(),
                BuildingRoiResponse.ComparisonMetric.builder()
                        .metric("Incident Response")
                        .before(responseTimeBefore)
                        .after(responseTimeAfter)
                        .unit("minutes")
                        .build()
        );
    }
}
