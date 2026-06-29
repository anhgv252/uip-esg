package com.uip.backend.billing.service;

import com.uip.backend.billing.domain.MeteringEvent;
import com.uip.backend.billing.domain.MeteringEventType;
import com.uip.backend.billing.domain.MonthlyUsage;
import com.uip.backend.billing.repository.MeteringEventRepository;
import com.uip.backend.billing.repository.MonthlyUsageRepository;
import com.uip.backend.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * M5-4 T01: Billing Aggregation Job (SP:5)
 * 
 * Daily scheduled job (1 AM UTC) that aggregates metering_events into monthly_usage table.
 * Calculates base fee + AI overage per building/month.
 * 
 * Billing model:
 * - Base fee: 2M VND/building/month
 * - AI tokens: 100K baseline, 50 VND per 1K tokens overage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingAggregationJob {

    private final MeteringEventRepository meteringEventRepository;
    private final MonthlyUsageRepository monthlyUsageRepository;
    private final TenantService tenantService;

    private static final long BASE_FEE_VND = 2_000_000L;        // 2M VND/building/month
    private static final long AI_TOKEN_BASELINE = 100_000L;      // 100K tokens included
    private static final long AI_OVERAGE_RATE = 50L;             // 50 VND per 1K tokens

    /**
     * Scheduled daily at 1 AM UTC.
     * Aggregates previous day's metering events into monthly_usage.
     * 
     * Cron: "0 0 1 * * *" = 1:00 AM every day
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void runDailyAggregation() {
        log.info("Starting daily billing aggregation job at 1 AM UTC");
        
        // Aggregate for current month (up to yesterday)
        YearMonth currentMonth = YearMonth.now();
        String billingMonth = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        aggregateMonthToDate(billingMonth);
        
        log.info("Daily billing aggregation completed for month: {}", billingMonth);
    }

    /**
     * Manual trigger for aggregation (used by admin API).
     * Aggregates for specified month or current month if null.
     * 
     * @param month YYYY-MM format (null = current month)
     */
    @Transactional
    public void aggregateMonth(String month) {
        String billingMonth = (month != null && !month.isBlank()) 
                ? month 
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        log.info("Manual billing aggregation triggered for month: {}", billingMonth);
        aggregateMonthToDate(billingMonth);
        log.info("Manual billing aggregation completed for month: {}", billingMonth);
    }

    /**
     * Core aggregation logic: group metering events by tenant + building + month.
     */
    private void aggregateMonthToDate(String billingMonth) {
        YearMonth targetMonth = YearMonth.parse(billingMonth, DateTimeFormatter.ofPattern("yyyy-MM"));
        Instant monthStart = targetMonth.atDay(1).atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant monthEnd = targetMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.of("UTC")).toInstant();

        // Get all active tenants
        List<String> tenantIds = tenantService.getAllTenantIds();
        
        for (String tenantId : tenantIds) {
            aggregateTenantMonth(tenantId, billingMonth, monthStart, monthEnd);
        }
    }

    private void aggregateTenantMonth(String tenantId, String billingMonth, Instant monthStart, Instant monthEnd) {
        log.debug("Aggregating billing for tenant {} in month {}", tenantId, billingMonth);
        
        // Fetch all metering events for this tenant in this month
        List<MeteringEvent> events = meteringEventRepository.findByTenantAndTimeRange(
                tenantId, monthStart, monthEnd);
        
        // Group by building ID
        Map<String, List<MeteringEvent>> eventsByBuilding = events.stream()
                .filter(e -> e.getBuildingId() != null)  // Skip null buildings
                .collect(Collectors.groupingBy(MeteringEvent::getBuildingId));
        
        for (Map.Entry<String, List<MeteringEvent>> entry : eventsByBuilding.entrySet()) {
            String buildingId = entry.getKey();
            List<MeteringEvent> buildingEvents = entry.getValue();
            
            aggregateBuildingMonth(tenantId, buildingId, billingMonth, buildingEvents);
        }
    }

    private void aggregateBuildingMonth(String tenantId, String buildingId, String billingMonth, 
                                        List<MeteringEvent> events) {
        // Calculate metrics
        long sensorReadings = events.stream()
                .filter(e -> e.getEventType() == MeteringEventType.SENSOR_READING)
                .count();
        
        long aiInferences = events.stream()
                .filter(e -> e.getEventType() == MeteringEventType.AI_INFERENCE 
                          || e.getEventType() == MeteringEventType.AI_PREDICTION)
                .count();
        
        long totalTokens = events.stream()
                .mapToLong(e -> e.getTokenCount() != null ? e.getTokenCount() : 0)
                .sum();
        
        long alerts = events.stream()
                .filter(e -> e.getEventType() == MeteringEventType.ALERT_GENERATED)
                .count();
        
        long workflows = events.stream()
                .filter(e -> e.getEventType() == MeteringEventType.BPMN_WORKFLOW_EXECUTED)
                .count();
        
        // Calculate costs
        long baseFee = BASE_FEE_VND;
        long aiOverage = calculateAiOverage(totalTokens);
        long totalCost = baseFee + aiOverage;
        
        // Upsert monthly_usage record
        MonthlyUsage usage = monthlyUsageRepository
                .findByTenantIdAndBuildingIdAndBillingMonth(tenantId, buildingId, billingMonth)
                .orElse(MonthlyUsage.builder()
                        .tenantId(tenantId)
                        .buildingId(buildingId)
                        .billingMonth(billingMonth)
                        .build());
        
        usage.setTotalSensorReadings(sensorReadings);
        usage.setTotalAiInferences(aiInferences);
        usage.setTotalAiTokens(totalTokens);
        usage.setTotalAlerts(alerts);
        usage.setTotalWorkflowExecutions(workflows);
        usage.setBaseFeeVnd(baseFee);
        usage.setAiOverageVnd(aiOverage);
        usage.setTotalCostVnd(totalCost);
        usage.setAggregatedAt(Instant.now());
        
        monthlyUsageRepository.save(usage);
        
        log.debug("Aggregated billing for tenant {} building {} month {}: baseFee={} aiOverage={} total={}",
                tenantId, buildingId, billingMonth, baseFee, aiOverage, totalCost);
    }

    /**
     * Calculate AI token overage cost.
     * Formula: (tokens - baseline) / 1000 * rate
     * 
     * @param totalTokens Total AI tokens used
     * @return Overage cost in VND
     */
    private long calculateAiOverage(long totalTokens) {
        if (totalTokens <= AI_TOKEN_BASELINE) {
            return 0L;
        }
        long overageTokens = totalTokens - AI_TOKEN_BASELINE;
        return (overageTokens / 1000) * AI_OVERAGE_RATE;
    }
}
