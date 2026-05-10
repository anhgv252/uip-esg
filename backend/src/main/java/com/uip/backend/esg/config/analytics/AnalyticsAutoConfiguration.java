package com.uip.backend.esg.config.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Demo extraction pattern (ADR-011): Analytics module trong monolith.
 *
 * Khi analytics-external = false (default):
 *   → TimescaleDbAnalyticsAdapter được load — ESG/Environment queries chạy
 *     trên TimescaleDB Continuous Aggregates (đủ cho T1, <500 sensors).
 *
 * Khi analytics-external = true (Tier 2+, sau Sprint 2 cutover):
 *   → Bean này bị skip. analytics-service riêng (ClickHouse owner) phục vụ
 *     cross-building OLAP queries. Monolith gọi analytics-service qua REST
 *     hoặc Port Interface swap sang HTTP adapter.
 *
 * Swap implementation không cần đổi business code:
 *   interface AnalyticsPort { EsgAggregateResult query(EsgAggregateQuery q); }
 *   @ConditionalOnProperty(analytics-external=false) TimescaleDbAnalyticsAdapter
 *   @ConditionalOnProperty(analytics-external=true)  ClickHouseRestAnalyticsAdapter
 */
@Configuration
@ConditionalOnProperty(
    name           = "uip.capabilities.analytics-external",
    havingValue    = "false",
    matchIfMissing = true   // ← Tier 1 không set → TimescaleDB adapter load bình thường
)
@Slf4j
public class AnalyticsAutoConfiguration {

    @Bean
    public AnalyticsPort analyticsPort() {
        log.info("[Capability] analytics-external=false " +
                 "→ TimescaleDbAnalyticsAdapter loaded (Tier 1 monolith mode)");
        return new TimescaleDbAnalyticsAdapter();
    }
}
