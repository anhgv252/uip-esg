package com.uip.backend.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting for login endpoint.
 * Capacity is configurable via security.login.rate-limit.capacity (default 5/min).
 */
@Component
@Slf4j
public class LoginRateLimitService {

    private static final int MAX_BUCKETS = 10_000;

    private final Bandwidth loginLimit;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();

    public LoginRateLimitService(
            @Value("${security.login.rate-limit.capacity:5}") int capacity) {
        this.loginLimit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(1))
                .build();
    }

    public boolean tryConsume(String clientIp) {
        lastAccess.put(clientIp, System.currentTimeMillis());
        Bucket bucket = buckets.computeIfAbsent(clientIp,
                ip -> Bucket.builder().addLimit(loginLimit).build());
        return bucket.tryConsume(1);
    }

    @Scheduled(fixedDelay = 600_000)
    void evictStaleBuckets() {
        long cutoff = System.currentTimeMillis() - 600_000;
        lastAccess.entrySet().removeIf(e -> {
            if (e.getValue() < cutoff) {
                buckets.remove(e.getKey());
                return true;
            }
            return false;
        });
        if (buckets.size() > MAX_BUCKETS) {
            log.warn("Login rate-limit bucket map size={} exceeds limit={}, force-evicting oldest entries",
                    buckets.size(), MAX_BUCKETS);
            lastAccess.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(buckets.size() - MAX_BUCKETS / 2)
                    .forEach(e -> { buckets.remove(e.getKey()); lastAccess.remove(e.getKey()); });
        }
    }
}
