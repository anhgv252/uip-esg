package com.uip.backend.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TenantRateLimiter {

    @Value("${uip.rate-limit.default.requests-per-minute:10000}")
    private int defaultRpm;

    // tenantId → Bucket
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Internal service IDs that bypass rate limiting
    private static final Set<String> WHITELIST = Set.of(
        "flink-internal", "prometheus-scraper", "monitoring"
    );

    public boolean tryConsume(String tenantId) {
        if (WHITELIST.contains(tenantId)) return true;
        return getBucket(tenantId).tryConsume(1);
    }

    public long getAvailableTokens(String tenantId) {
        return getBucket(tenantId).getAvailableTokens();
    }

    private Bucket getBucket(String tenantId) {
        return buckets.computeIfAbsent(tenantId, id -> {
            Bandwidth limit = Bandwidth.builder()
                .capacity(defaultRpm)
                .refillIntervally(defaultRpm, Duration.ofMinutes(1))
                .build();
            log.debug("Created rate-limit bucket for tenant={} rpm={}", id, defaultRpm);
            return Bucket.builder().addLimit(limit).build();
        });
    }
}
