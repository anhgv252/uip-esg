package com.uip.backend.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting for login endpoint: 5 attempts per minute.
 * Buckets are lazily created and held in memory (ConcurrentHashMap).
 * Under normal load (<50 distinct IPs/min) memory footprint is negligible.
 */
@Component
public class LoginRateLimitService {

    private static final Bandwidth LOGIN_LIMIT =
            Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofMinutes(1)).build();

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent(clientIp,
                ip -> Bucket.builder().addLimit(LOGIN_LIMIT).build());
        return bucket.tryConsume(1);
    }
}
