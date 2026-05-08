package com.uip.backend.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MVP2-14: Redis-backed rate limiter per tenant.
 * Uses atomic Lua INCR+EXPIRE for sliding per-minute windows in Redis.
 * Falls back to Bucket4j in-memory when Redis is unavailable (T1 / no-Redis deployments).
 */
@Slf4j
@Service
public class TenantRateLimiter {

    @Value("${uip.rate-limit.default.requests-per-minute:10000}")
    private int defaultRpm;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final Map<String, Bucket> inMemoryBuckets = new ConcurrentHashMap<>();

    private static final Set<String> WHITELIST = Set.of(
        "flink-internal", "prometheus-scraper", "monitoring"
    );

    // Atomic: INCR + EXPIRE on first creation; returns current count.
    // TTL 61s covers a full minute window even with slight clock drift.
    private static final String RATE_LIMIT_SCRIPT =
        "local count = redis.call('INCR', KEYS[1])\n" +
        "if count == 1 then redis.call('EXPIRE', KEYS[1], 61) end\n" +
        "return count";

    public boolean tryConsume(String tenantId) {
        if (WHITELIST.contains(tenantId)) return true;
        if (redisTemplate != null) {
            return tryConsumeRedis(tenantId);
        }
        return tryConsumeInMemory(tenantId);
    }

    public long getAvailableTokens(String tenantId) {
        if (redisTemplate != null) {
            try {
                String val = redisTemplate.opsForValue().get(redisKey(tenantId));
                long count = val != null ? Long.parseLong(val) : 0L;
                return Math.max(0L, defaultRpm - count);
            } catch (Exception e) {
                // fall through to in-memory estimate
            }
        }
        return getBucket(tenantId).getAvailableTokens();
    }

    private boolean tryConsumeRedis(String tenantId) {
        try {
            RedisScript<Long> script = RedisScript.of(RATE_LIMIT_SCRIPT, Long.class);
            Long count = redisTemplate.execute(script, List.of(redisKey(tenantId)));
            if (count == null) {
                return tryConsumeInMemory(tenantId);
            }
            return count <= defaultRpm;
        } catch (Exception e) {
            log.warn("Redis rate-limit error tenant={}, falling back to in-memory: {}", tenantId, e.getMessage());
            return tryConsumeInMemory(tenantId);
        }
    }

    private boolean tryConsumeInMemory(String tenantId) {
        return getBucket(tenantId).tryConsume(1);
    }

    private String redisKey(String tenantId) {
        long minuteWindow = System.currentTimeMillis() / 60_000L;
        return "rate-limit:" + tenantId + ":" + minuteWindow;
    }

    private Bucket getBucket(String tenantId) {
        return inMemoryBuckets.computeIfAbsent(tenantId, id -> {
            Bandwidth limit = Bandwidth.builder()
                .capacity(defaultRpm)
                .refillIntervally(defaultRpm, Duration.ofMinutes(1))
                .build();
            log.debug("Created in-memory rate-limit bucket for tenant={} rpm={}", id, defaultRpm);
            return Bucket.builder().addLimit(limit).build();
        });
    }
}
