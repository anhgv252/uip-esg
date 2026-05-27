package com.uip.backend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Central registry cho tất cả capability flags (ADR-011).
 *
 * Hai nhóm:
 *   1. Feature flags    — bật tính năng Tier cao (multi-tenancy, clickhouse…)
 *   2. Extraction flags — tắt module khi đã externalize ra service riêng
 *
 * Tier 1 (1-2 tòa): không set bất kỳ flag nào. matchIfMissing=true trên
 * @ConditionalOnProperty đảm bảo monolith chạy đầy đủ, Tier 1 không bị ảnh hưởng.
 *
 * Tier 2 (5-20 tòa): set trong application-t2.yml hoặc env var
 *   UIP_CAPABILITIES_IOT_INGESTION_EXTERNAL=true
 *   UIP_CAPABILITIES_ANALYTICS_EXTERNAL=true
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "uip.capabilities")
public class CapabilityProperties {

    // ── Feature flags ──────────────────────────────────────────────────────
    private boolean multiTenancy = false;
    private boolean redisCache = false;
    private boolean clickhouse = false;
    private boolean kongGateway = false;
    private boolean keycloak = false;
    private boolean edgeComputing = false;
    private boolean multiRegion = false;

    // ── Extraction flags ───────────────────────────────────────────────────
    // true = external service đảm nhận; monolith bean bị disable bởi
    // @ConditionalOnProperty(..., matchIfMissing = true)
    private boolean iotIngestionExternal = false;   // ADR-011 order #1
    private boolean alertExternal = false;           // ADR-011 order #2
    private boolean analyticsExternal = false;       // ADR-011 order #3
    private boolean aiWorkflowExternal = false;      // ADR-011 order #4
    private boolean citizenExternal = false;         // ADR-011 order #5

    // ── Forecast engine selector (ADR-032) ─────────────────────────────────
    // "python" = ForecastServiceAdapter (REST → Python forecast-service)
    // "naive"  = NaiveForecastAdapter (in-process rolling average)
    // "disabled" = no bean → controller returns 501
    private String forecastEngine = "python";
}
