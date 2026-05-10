package com.uip.backend.esg.config.analytics;

import com.uip.backend.esg.repository.EsgMetricRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tier 1 auto-configuration: load TimescaleDbAnalyticsAdapter khi analytics-external=false.
 * matchIfMissing=true → Tier 1 không cần set bất kỳ property nào.
 * Tier 2 set analytics-external=true → bean này bị skip, ClickHouseRestAnalyticsAdapter load.
 */
@Configuration
@ConditionalOnProperty(
    name           = "uip.capabilities.analytics-external",
    havingValue    = "false",
    matchIfMissing = true
)
@Slf4j
public class AnalyticsAutoConfiguration {

    @Bean
    public AnalyticsPort analyticsPort(EsgMetricRepository metricRepository) {
        log.info("[Capability] analytics-external=false → TimescaleDbAnalyticsAdapter loaded");
        return new TimescaleDbAnalyticsAdapter(metricRepository);
    }
}
