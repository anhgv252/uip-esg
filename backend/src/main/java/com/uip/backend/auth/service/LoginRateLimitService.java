package com.uip.backend.auth.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting for login endpoint.
 * Capacity is configurable via security.login.rate-limit.capacity (default 5/min).
 */
@Component
public class LoginRateLimitService {

    private final Bandwidth loginLimit;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimitService(
            @Value("${security.login.rate-limit.capacity:5}") int capacity) {
        this.loginLimit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofMinutes(1))
                .build();
    }

    public boolean tryConsume(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent(clientIp,
                ip -> Bucket.builder().addLimit(loginLimit).build());
        return bucket.tryConsume(1);
    }
}
