package com.uip.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "uip.rate-limit")
public class RateLimitConfig {

    private int defaultRequestsPerMinute = 10000;
    private Map<String, Integer> tenantOverrides = new HashMap<>();

    public int getDefaultRequestsPerMinute() { return defaultRequestsPerMinute; }
    public void setDefaultRequestsPerMinute(int v) { this.defaultRequestsPerMinute = v; }
    public Map<String, Integer> getTenantOverrides() { return tenantOverrides; }
    public void setTenantOverrides(Map<String, Integer> v) { this.tenantOverrides = v; }
}
