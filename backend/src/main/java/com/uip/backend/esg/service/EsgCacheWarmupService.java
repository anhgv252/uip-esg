package com.uip.backend.esg.service;

import com.uip.backend.tenant.domain.Tenant;
import com.uip.backend.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * BT-21a — ESG cache warmup on application startup.
 *
 * Triggered by {@link ApplicationReadyEvent} (after all beans initialised, Flyway migrations run).
 * Iterates over every active tenant and pre-populates the Redis ESG dashboard cache so the
 * first real request hits Redis instead of TimescaleDB.
 *
 * Failure policy: per-tenant exceptions are caught and logged as WARN; a single tenant failure
 * never aborts startup or prevents other tenants from warming up.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EsgCacheWarmupService {

    private final EsgService esgService;
    private final TenantRepository tenantRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void warmupCache() {
        List<Tenant> activeTenants = tenantRepository.findAll()
                .stream()
                .filter(Tenant::isActive)
                .toList();

        if (activeTenants.isEmpty()) {
            log.info("ESG cache warmup skipped — no active tenants found");
            return;
        }

        int currentYear    = LocalDate.now().getYear();
        int currentQuarter = (LocalDate.now().getMonthValue() - 1) / 3 + 1;

        log.info("Starting ESG cache warmup for {} active tenant(s)", activeTenants.size());

        for (Tenant tenant : activeTenants) {
            String tenantId = tenant.getTenantId();
            try {
                log.info("Warming ESG cache for tenant: {}", tenantId);

                // Quarterly summary for current quarter
                esgService.getSummary(tenantId, "QUARTERLY", currentYear, currentQuarter);

                // Annual summary for current year
                esgService.getSummary(tenantId, "ANNUAL", currentYear, 0);

                // Energy and carbon data (30-day window — uses null defaults in EsgService)
                esgService.getEnergyData(tenantId, null, null, null);
                esgService.getCarbonData(tenantId, null, null);

            } catch (Exception e) {
                log.warn("ESG cache warmup failed for tenant: {} — {}", tenantId, e.getMessage());
            }
        }

        log.info("ESG cache warmup completed for {} tenant(s)", activeTenants.size());
    }
}
