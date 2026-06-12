package com.uip.backend.ai;

import com.uip.backend.ai.budget.TokenBudgetService;
import com.uip.backend.ai.cache.AiCacheConfig;
import com.uip.backend.ai.cache.AiCacheMetrics;
import com.uip.backend.ai.cache.AqiRangeBucket;
import com.uip.backend.ai.routing.ModelRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * M4-AI-04: Central AI inference service for AQI district analysis.
 *
 * <h2>Caching strategy</h2>
 * <p>Identical requests are cached in Redis DB 2 under the key pattern
 * {@code ai-responses::{districtCode}:{aqiRange}} with a 5-minute TTL.
 * The AQI value is bucketed into one of six EPA-aligned bands
 * ({@link AqiRangeBucket}) before being used as part of the cache key,
 * so all readings within a band share a single cached response.</p>
 *
 * <h2>Hit-rate estimation</h2>
 * <pre>
 *   Scenario: 10 districts, polling every 60 s, AQI band changes every ~15 min.
 *   - Band duration = 15 min = 15 requests/district
 *   - Cache TTL = 5 min → 5 requests hit cache before expiry; then 1 miss + 4 hits
 *   - Net hit rate per district ≈ 4 / 5 = 80% (&gt;50% target satisfied)
 *   - With 10 districts in same AQI band → 10 × 80% = aggregate ≥80% hit rate
 * </pre>
 *
 * <h2>Metric tracking</h2>
 * <p>The public entry-point {@link #analyzeAqiWithMetrics} performs a cache lookup
 * before delegating to the {@code @Cacheable} method through a Spring proxy
 * (self-injection) to accurately detect hits vs misses and increment
 * {@link AiCacheMetrics} counters.</p>
 */
@Service
@Slf4j
public class AiInferenceService {

    // ─── Self-injection for @Cacheable proxy pass-through ─────────────────────

    /**
     * Lazily injected self-proxy. Required so that the call to
     * {@link #analyzeAqiConditions} goes through the Spring AOP proxy (where
     * {@code @Cacheable} interception happens). Direct {@code this.} calls bypass
     * the cache.
     */
    @Autowired
    @Lazy
    private AiInferenceService self;

    // ─── Dependencies ─────────────────────────────────────────────────────────

    private final CacheManager aiResponseCacheManager;
    private final AiCacheMetrics metrics;
    private final AiCostMetrics costMetrics;
    private final ModelRouter modelRouter;
    private final TokenBudgetService tokenBudgetService;
    private final RestTemplate restTemplate;

    // ─── Claude API config ────────────────────────────────────────────────────

    @Value("${claude.api.key:}")
    private String apiKey;

    @Value("${claude.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${claude.api.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${ai.token-budget.monthly-limit:1000000}")
    private long monthlyTokenLimit;

    public AiInferenceService(
            @Qualifier("aiResponseCacheManager") CacheManager aiResponseCacheManager,
            AiCacheMetrics metrics,
            AiCostMetrics costMetrics,
            ModelRouter modelRouter,
            TokenBudgetService tokenBudgetService) {
        this.aiResponseCacheManager = aiResponseCacheManager;
        this.metrics                = metrics;
        this.costMetrics            = costMetrics;
        this.modelRouter            = modelRouter;
        this.tokenBudgetService     = tokenBudgetService;
        this.restTemplate           = new RestTemplate();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Analyses AQI conditions for a district, using Redis cache to avoid
     * redundant Claude API calls.
     *
     * <p>Cache key: {@code ai-responses::{districtCode}:{aqiRange}}</p>
     *
     * <p>Metrics are tracked by pre-checking the cache before invoking the
     * {@code @Cacheable} proxy ({@link #analyzeAqiConditions}) to determine
     * whether the result comes from cache (hit) or from a fresh API call (miss).</p>
     *
     * @param districtCode district identifier, e.g. "HCM-D1"
     * @param aqi          raw AQI sensor reading
     * @return analysis response (may be served from cache)
     */
    public AiAnalysisResponse analyzeAqiWithMetrics(String districtCode, double aqi) {
        String aqiRange  = AqiRangeBucket.bucket(aqi);
        String cacheKey  = districtCode + ":" + aqiRange;
        Cache  cache     = aiResponseCacheManager.getCache(AiCacheConfig.CACHE_NAME);
        boolean preExisted = (cache != null && cache.get(cacheKey) != null);

        // Delegate through proxy so @Cacheable interception applies
        AiAnalysisResponse response = self.analyzeAqiConditions(districtCode, aqi);

        if (preExisted) {
            metrics.recordHit();
            costMetrics.recordCacheHit("default");
            log.debug("[AiInference] Cache HIT: district={} aqiRange={}", districtCode, aqiRange);
        } else {
            metrics.recordMiss();
            log.debug("[AiInference] Cache MISS: district={} aqiRange={}", districtCode, aqiRange);
        }
        return response;
    }

    // ─── @Cacheable method (must be called through Spring proxy) ─────────────

    /**
     * Core inference method — annotated with {@code @Cacheable} so identical
     * {@code districtCode + aqiRange} pairs are served from Redis without
     * contacting the Claude API.
     *
     * <p><strong>Do not call this method directly from within this class.</strong>
     * Use {@link #analyzeAqiWithMetrics} to ensure cache interception and metric
     * tracking are applied. Direct calls via {@code this.} bypass the AOP proxy.</p>
     *
     * @param districtCode district identifier
     * @param aqi          raw AQI reading (bucketed internally for the cache key)
     * @return fresh or cached {@link AiAnalysisResponse}
     */
    @Cacheable(
            value         = AiCacheConfig.CACHE_NAME,
            key           = "#districtCode + ':' + T(com.uip.backend.ai.cache.AqiRangeBucket).bucket(#aqi)",
            cacheManager  = "aiResponseCacheManager",
            condition     = "#districtCode != null"
    )
    public AiAnalysisResponse analyzeAqiConditions(String districtCode, double aqi) {
        log.info("[AiInference] Calling Claude API: district={} aqi={}", districtCode, aqi);

        if (!tokenBudgetService.isWithinBudget(0)) {
            log.warn("[AiInference] Token budget exceeded — returning fallback");
            return AiAnalysisResponse.fallback(districtCode, AqiRangeBucket.bucket(aqi));
        }

        if (!org.springframework.util.StringUtils.hasText(apiKey)) {
            log.warn("[AiInference] CLAUDE_API_KEY not configured — returning fallback");
            return AiAnalysisResponse.fallback(districtCode, AqiRangeBucket.bucket(aqi));
        }

        try {
            return callClaudeApi(districtCode, aqi);
        } catch (RestClientException e) {
            log.error("[AiInference] Claude API call failed: {}", e.getMessage());
            return AiAnalysisResponse.fallback(districtCode, AqiRangeBucket.bucket(aqi));
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private AiAnalysisResponse callClaudeApi(String districtCode, double aqi) {
        String aqiRange = AqiRangeBucket.bucket(aqi);
        String model    = modelRouter.selectModel(200, "LOW"); // AQI summaries are low-complexity

        String prompt = String.format(
                "Provide a concise 2-sentence air quality advisory for district %s " +
                "with AQI in the %s range. Focus on public health guidance.",
                districtCode, aqiRange);

        Map<String, Object> requestBody = Map.of(
                "model",      model,
                "max_tokens", 200,
                "messages",   List.of(Map.of("role", "user", "content", prompt))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                new ParameterizedTypeReference<>() {}
        );

        String recommendation = extractText(response.getBody());

        // M4-AI-06: Record token usage and estimated cost
        int[] usage = extractTokenUsage(response.getBody());
        costMetrics.recordCall(model, "default", usage[0], usage[1]);

        return new AiAnalysisResponse(districtCode, aqiRange, recommendation, model,
                System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> body) {
        if (body == null) return "No response from AI model.";
        try {
            List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
            if (content != null && !content.isEmpty()) {
                Object text = content.get(0).get("text");
                return text != null ? text.toString() : "Empty AI response.";
            }
        } catch (ClassCastException e) {
            log.warn("[AiInference] Unexpected Claude API response shape");
        }
        return "Unable to parse AI response.";
    }

    /**
     * Extracts token usage from the Claude API response body.
     * Returns [inputTokens, outputTokens]. Falls back to [0, 0] if usage is absent.
     */
    @SuppressWarnings("unchecked")
    private int[] extractTokenUsage(Map<String, Object> body) {
        if (body == null) return new int[]{0, 0};
        try {
            Map<String, Object> usage = (Map<String, Object>) body.get("usage");
            if (usage != null) {
                int input  = usage.get("input_tokens")  instanceof Number n ? n.intValue() : 0;
                int output = usage.get("output_tokens") instanceof Number n ? n.intValue() : 0;
                return new int[]{input, output};
            }
        } catch (ClassCastException e) {
            log.warn("[AiInference] Unexpected Claude API usage field shape");
        }
        return new int[]{0, 0};
    }
}
