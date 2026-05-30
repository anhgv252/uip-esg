package com.uip.backend.aiworkflow.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * S6-AI03 — Decision Router: confidence-based routing for AI decisions.
 *
 * Routes AI decisions based on confidence score:
 *   > 0.85 → AUTO_EXECUTE  (action taken automatically)
 *   0.6–0.85 → OPERATOR_QUEUE (requires operator approval)
 *   < 0.6 → ESCALATE (escalate to supervisor)
 *
 * Includes Redis cache for similar decisions (TTL 15 min) to reduce
 * redundant AI calls for similar contexts.
 */
@Component
@Slf4j
public class DecisionRouter {

    private static final double AUTO_THRESHOLD = 0.85;
    private static final double ESCALATE_THRESHOLD = 0.6;
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final String CACHE_PREFIX = "ai:decision:cache:";

    private final StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public DecisionRouter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /**
     * Route an AI decision based on confidence score.
     *
     * @param decision the AI decision to route
     * @return routing result with action and cached flag
     */
    public RoutingResult route(AiDecisionInput decision) {
        double confidence = decision.getConfidence();
        RoutingAction action = classify(confidence);

        log.info("Decision routed: action={} confidence={} decision={}",
                action, confidence, decision.getDecision());

        return new RoutingResult(action, decision.getDecision(),
                decision.getReasoning(), confidence, false);
    }

    /**
     * Route with cache lookup — checks Redis for a similar decision first.
     *
     * @param scenarioKey the scenario identifier (e.g. "flood-alert")
     * @param context the context hash input (sensor data, rules, etc.)
     * @param decision the AI decision
     * @return routing result (may be cached)
     */
    public RoutingResult routeWithCache(String scenarioKey, String context, AiDecisionInput decision) {
        String cacheKey = buildCacheKey(scenarioKey, context);

        // Check cache first
        Optional<RoutingResult> cached = lookupCache(cacheKey);
        if (cached.isPresent()) {
            log.debug("Decision cache hit: key={}", cacheKey);
            return cached.get();
        }

        // Route new decision
        RoutingResult result = route(decision);

        // Cache the result
        cacheResult(cacheKey, result);

        return result;
    }

    RoutingAction classify(double confidence) {
        if (confidence > AUTO_THRESHOLD) return RoutingAction.AUTO_EXECUTE;
        if (confidence >= ESCALATE_THRESHOLD) return RoutingAction.OPERATOR_QUEUE;
        return RoutingAction.ESCALATE;
    }

    private String buildCacheKey(String scenarioKey, String context) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(context.getBytes(StandardCharsets.UTF_8));
            String hexHash = bytesToHex(hash);
            return CACHE_PREFIX + scenarioKey + ":" + hexHash;
        } catch (Exception e) {
            // Fallback to raw key (should never happen with SHA-256)
            return CACHE_PREFIX + scenarioKey + ":" + context.hashCode();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<RoutingResult> lookupCache(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                Map<String, Object> map = objectMapper.readValue(cached, Map.class);
                return Optional.of(new RoutingResult(
                        RoutingAction.valueOf((String) map.get("action")),
                        (String) map.get("decision"),
                        (String) map.get("reasoning"),
                        ((Number) map.get("confidence")).doubleValue(),
                        true
                ));
            }
        } catch (Exception e) {
            log.debug("Cache lookup failed for key={}: {}", cacheKey, e.getMessage());
        }
        return Optional.empty();
    }

    private void cacheResult(String cacheKey, RoutingResult result) {
        try {
            Map<String, Object> map = Map.of(
                    "action", result.action().name(),
                    "decision", result.decision(),
                    "reasoning", result.reasoning(),
                    "confidence", result.confidence()
            );
            String json = objectMapper.writeValueAsString(map);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.debug("Cache write failed for key={}: {}", cacheKey, e.getMessage());
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // --- Inner types ---

    public enum RoutingAction {
        AUTO_EXECUTE,
        OPERATOR_QUEUE,
        ESCALATE
    }

    /**
     * Result of routing an AI decision.
     */
    public record RoutingResult(
            RoutingAction action,
            String decision,
            String reasoning,
            double confidence,
            boolean cached
    ) {}
}
