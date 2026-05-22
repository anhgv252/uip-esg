package com.uip.backend.common.ratelimit;

import com.uip.backend.tenant.repository.TenantConfigRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MVP2-14: Redis-backed rate limiter per tenant.
 * Uses atomic Lua INCR+EXPIRE for sliding per-minute windows in Redis.
 * Falls back to Bucket4j in-memory when Redis is unavailable (T1 / no-Redis deployments).
 * Per-tenant RPM is configurable via tenant_config key=rate-limit.requests-per-minute.
 */
@Slf4j
@Service
public class TenantRateLimiter {

    private static final String RPM_CONFIG_KEY = "rate-limit.requests-per-minute";

    @Value("${uip.rate-limit.default.requests-per-minute:10000}")
    private int defaultRpm;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private TenantConfigRepository tenantConfigRepository;

    private final Map<String, Bucket> inMemoryBuckets = new ConcurrentHashMap<>();
    // tenantId -> configured RPM; falls back to defaultRpm when absent
    private final AtomicReference<Map<String, Integer>> tenantRpmMap =
            new AtomicReference<>(Map.of());

    private static final Set<String> WHITELIST = Set.of(
        "flink-internal", "prometheus-scraper", "monitoring"
    );

    // Atomic: INCR + EXPIRE on first creation; returns current count.
    // TTL 61s covers a full minute window even with slight clock drift.
    private static final String RATE_LIMIT_SCRIPT =
        "local count = redis.call('INCR', KEYS[1])\n" +
        "if count == 1 then redis.call('EXPIRE', KEYS[1], 61) end\n" +
        "return count";

    @PostConstruct
    public void init() {
        reloadTenantRpm();
    }

    @Scheduled(fixedDelay = 300_000)
    public void reloadTenantRpm() {
        if (tenantConfigRepository == null) return;
        Map<String, Integer> map = new HashMap<>();
        tenantConfigRepository.findAllByConfigKey(RPM_CONFIG_KEY).forEach(entry -> {
            try {
                map.put(entry.getTenantId(), Integer.parseInt(entry.getConfigValue().trim()));
            } catch (NumberFormatException e) {
                log.warn("Invalid rate-limit RPM for tenant={}: {}", entry.getTenantId(), entry.getConfigValue());
            }
        });
        tenantRpmMap.set(Map.copyOf(map));
        log.debug("Rate-limit RPM config reloaded: {} tenant-specific limits", map.size());
    }

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
                return Math.max(0L, getRpmForTenant(tenantId) - count);
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
            return count <= getRpmForTenant(tenantId);
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

    private int getRpmForTenant(String tenantId) {
        return tenantRpmMap.get().getOrDefault(tenantId, defaultRpm);
    }

    private Bucket getBucket(String tenantId) {
        return inMemoryBuckets.computeIfAbsent(tenantId, id -> {
            int rpm = getRpmForTenant(id);
            Bandwidth limit = Bandwidth.builder()
                .capacity(rpm)
                .refillIntervally(rpm, Duration.ofMinutes(1))
                .build();
            log.debug("Created in-memory rate-limit bucket for tenant={} rpm={}", id, rpm);
            return Bucket.builder().addLimit(limit).build();
        });
    }
}
