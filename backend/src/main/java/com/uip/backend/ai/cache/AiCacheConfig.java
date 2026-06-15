package com.uip.backend.ai.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * M4-AI-04: Cache configuration for AI inference responses.
 *
 * <p>A single bean {@code aiResponseCacheManager} is registered, backed by either
 * Redis (production) or an in-memory {@link ConcurrentMapCacheManager} (tests),
 * selected by {@code spring.cache.type}. Both bean methods live in this class but
 * carry mutually-exclusive {@code @ConditionalOnProperty} so exactly one fires —
 * this avoids the {@code BeanDefinitionOverrideException} that two same-named
 * {@code @Bean} methods in separate nested classes would trigger.</p>
 *
 * <h2>Redis DB isolation</h2>
 * <p>AI responses are stored in Redis DB 2, separate from DB 0 (Spring Session,
 * rate-limiter tokens, general {@code @Cacheable} data). Isolation prevents AI
 * cache eviction (allkeys-lru) from displacing session data, and allows
 * independent {@code FLUSHDB} for the AI cache without affecting sessions.</p>
 *
 * <h2>Redis server config (applied via docker-compose command args)</h2>
 * <pre>
 *   --maxmemory 512mb
 *   --maxmemory-policy allkeys-lru
 * </pre>
 */
@Configuration
public class AiCacheConfig {

    /** Cache name used in {@code @Cacheable} annotations throughout the AI package. */
    public static final String CACHE_NAME = "ai-responses";

    /** TTL for AI inference responses in Redis (5 minutes). */
    public static final Duration AI_RESPONSE_TTL = Duration.ofSeconds(300);

    /** Bean name referenced by {@code @Cacheable(cacheManager = "aiResponseCacheManager")}. */
    public static final String CACHE_MANAGER_BEAN = "aiResponseCacheManager";

    // ─── Redis-backed cache (production / staging) ────────────────────────────

    /**
     * Dedicated Lettuce connection factory pointing at Redis DB 2.
     * Only created when {@code spring.cache.type=redis} (or missing → matchIfMissing).
     */
    @Bean("aiRedisConnectionFactory")
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public RedisConnectionFactory aiRedisConnectionFactory() {
        // values injected via reflection-free field access; see field declarations below
        return aiRedisConnectionFactory(redisHost, redisPort, redisPassword, aiCacheDatabase);
    }

    /**
     * Cache manager for AI responses — TTL 300 s, JSON serialisation with type info.
     * Named {@code aiResponseCacheManager} so {@code @Cacheable} annotations in
     * {@code AiInferenceService} can reference it explicitly.
     */
    @Bean(CACHE_MANAGER_BEAN)
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public CacheManager aiResponseCacheManager(
            @org.springframework.beans.factory.annotation.Qualifier("aiRedisConnectionFactory")
            RedisConnectionFactory factory) {

        RedisCacheConfiguration cfg = buildCacheConfig(AI_RESPONSE_TTL);
        return RedisCacheManager.builder(factory)
                .cacheDefaults(cfg)
                .withCacheConfiguration(CACHE_NAME, cfg)
                .build();
    }

    // ─── In-memory fallback (test profile / spring.cache.type != redis) ───────

    /**
     * Simple in-memory cache used when Redis is not available
     * (e.g. tests with {@code spring.cache.type=simple}).
     *
     * <p>Uses a {@link ConcurrentMapCacheManager} <em>without</em> a fixed cache-name
     * list so it lazily creates any cache on demand. This preserves the Spring Boot
     * default behaviour for {@code @Cacheable}/{@code @CacheEvict} on other caches
     * (alerts, sensors, esg-dashboard, trigger-configs) that would otherwise break
     * when this named bean displaces the auto-configured generic CacheManager.</p>
     */
    @Bean(CACHE_MANAGER_BEAN)
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple")
    public CacheManager aiResponseFallbackCacheManager() {
        return new ConcurrentMapCacheManager();
    }

    /**
     * No-op fallback for tests that disable caching entirely via
     * {@code spring.cache.type=none}. Without this bean, {@link AiInferenceService}'s
     * {@code @Qualifier("aiResponseCacheManager")} dependency has nothing to wire and
     * the ApplicationContext fails to load (see feedback_mvp4_config_bugs — full
     * @SpringBootTest surfaced this). ConcurrentMapCacheManager is cheap and correct
     * here: caching is already semantically disabled by the test profile, the bean
     * merely satisfies the DI contract.
     */
    @Bean(CACHE_MANAGER_BEAN)
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "none")
    public CacheManager aiResponseNoOpCacheManager() {
        return new ConcurrentMapCacheManager();
    }

    // ─── Config values (injected on the outer @Configuration class) ───────────

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${ai.cache.redis-database:2}")
    private int aiCacheDatabase;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Builds the Redis connection factory from explicit parameters (testable). */
    static RedisConnectionFactory aiRedisConnectionFactory(
            String host, int port, String password, int database) {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
        cfg.setDatabase(database);
        if (StringUtils.hasText(password)) {
            cfg.setPassword(password);
        }
        return new LettuceConnectionFactory(cfg);
    }

    private static RedisCacheConfiguration buildCacheConfig(Duration ttl) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
    }
}
