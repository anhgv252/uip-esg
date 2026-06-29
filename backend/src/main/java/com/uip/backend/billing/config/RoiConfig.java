package com.uip.backend.billing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * M5-3 T06: ROI calculation configuration.
 * 
 * Pricing model (per D4 Decision):
 * - Base fee: 2,000,000 VND/building/month
 * - AI tokens: 100,000 base allocation, 50 VND per 1,000 tokens overage
 * - Manual ops baseline: 8,500,000 VND/building/month (for savings calculation)
 * - CO2 factor: 0.5 kg CO2 per kWh saved (VN grid emission factor)
 */
@Configuration
@ConfigurationProperties(prefix = "roi")
@Getter
@Setter
public class RoiConfig {

    /**
     * Manual operations cost baseline (VND/building/month).
     * Used to calculate automation savings.
     * Default: 8,500,000 VND (estimated manual monitoring cost).
     */
    private Long manualOpsBaselineVnd = 8_500_000L;

    /**
     * Base subscription fee (VND/building/month).
     * Default: 2,000,000 VND.
     */
    private Long baseFeeVnd = 2_000_000L;

    /**
     * AI token overage rate (VND per 1,000 tokens).
     * Default: 50 VND/1000 tokens.
     */
    private Long tokenRateVndPerThousand = 50L;

    /**
     * AI token base allocation (tokens/building/month).
     * No overage charge for tokens below this threshold.
     * Default: 100,000 tokens.
     */
    private Long tokenBaseAllocation = 100_000L;

    /**
     * CO2 emission factor (kg CO2 per kWh saved).
     * Used for environmental impact calculation.
     * Default: 0.5 kg CO2/kWh (Vietnam grid emission factor).
     */
    private BigDecimal co2KgPerKwhSaved = new BigDecimal("0.5");

    /**
     * Default energy savings percentage when using UIP automation.
     * Default: 0.15 (15% reduction in energy consumption).
     */
    private BigDecimal energySavingsFactor = new BigDecimal("0.15");

    /**
     * Average energy consumption per building (kWh/month) before UIP.
     * Used for CO2 calculation when actual data is not available.
     * Default: 15,000 kWh/month.
     */
    private Long avgEnergyConsumptionKwh = 15_000L;
}
