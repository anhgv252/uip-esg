package com.uip.backend.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory token blacklist for demo/single-instance use.
 * Stores invalidated JWT tokens until they would naturally expire.
 * Not suitable for multi-instance deployments (use Redis instead).
 */
@Service
@Slf4j
public class TokenBlacklistService {

    // token → expiry timestamp in millis
    private final ConcurrentMap<String, Long> blacklist = new ConcurrentHashMap<>();

    /**
     * Add a token to the blacklist until it expires.
     *
     * @param token     raw JWT string
     * @param expiresAt Unix timestamp (ms) when the token would naturally expire
     */
    public void invalidate(String token, long expiresAt) {
        blacklist.put(token, expiresAt);
        log.debug("Token invalidated, blacklist size={}", blacklist.size());
    }

    public boolean isBlacklisted(String token) {
        return blacklist.containsKey(token);
    }

    /** Remove entries for already-expired tokens every 10 minutes. */
    @Scheduled(fixedDelay = 600_000)
    void evictExpired() {
        long now = System.currentTimeMillis();
        int before = blacklist.size();
        blacklist.entrySet().removeIf(e -> e.getValue() < now);
        int removed = before - blacklist.size();
        if (removed > 0) {
            log.debug("Evicted {} expired blacklist entries", removed);
        }
    }
}
