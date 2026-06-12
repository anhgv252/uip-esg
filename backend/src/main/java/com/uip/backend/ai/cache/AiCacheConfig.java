package com.uip.backend.ai.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * M4-AI-04: Redis cache configuration for AI inference responses.
 *
 * <h2>Redis DB isolation</h2>
 * <p>AI responses are stored in Redis DB 2, separate from:
 * <ul>
 *   <li>DB 0 — Spring Session, rate-limiter tokens, general @Cacheable data</li>
 *   <li>DB 2 — AI inference responses (this config)</li>
 * </ul>
 * Isolation prevents AI cache eviction (allkeys-lru) from displacing session
 * data, and allows independent FLUSHDB for AI cache without affecting sessions.</p>
 *
 * <h2>Redis server config (applied via docker-compose command args)</h2>
 * <pre>
 *   --maxmemory 512mb
 *   --maxmemory-policy allkeys-lru
 * </pre>
 * The 512 MB budget is shared across all DBs; 256 MB is the effective ceiling for
 * AI responses as documented in the task spec.
 *
 * <h2>Conditional wiring</h2>
 * <ul>
 *   <li>When {@code spring.cache.type=redis} (default) → {@link RedisAiCacheConfiguration}
 *       creates a dedicated {@link LettuceConnectionFactory} for DB 2 and a
 *       {@code RedisCacheManager} named {@code aiResponseCacheManager}.</li>
 *   <li>When {@code spring.cache.type=simple} (test profile) →
 *       {@link FallbackAiCacheConfiguration} creates a {@link ConcurrentMapCacheManager}
 *       so {@code @Cacheable(cacheManager="aiResponseCacheManager")} remains a no-op
 *       in unit/integration tests without Redis.</li>
 * </ul>
 */
public class AiCacheConfig {

    /** Cache name used in {@code @Cacheable} annotations throughout the AI package. */
    public static final String CACHE_NAME = "ai-responses";

    /** TTL for AI inference responses in Redis (5 minutes). */
    public static final Duration AI_RESPONSE_TTL = Duration.ofSeconds(300);

    // ─── Redis-backed cache (production / staging) ────────────────────────────

    @Configuration
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    static class RedisAiCacheConfiguration {

        @Value("${spring.data.redis.host:localhost}")
        private String redisHost;

        @Value("${spring.data.redis.port:6379}")
        private int redisPort;

        @Value("${spring.data.redis.password:}")
        private String redisPassword;

        @Value("${ai.cache.redis-database:2}")
        private int aiCacheDatabase;

        /**
         * Dedicated Lettuce connection factory pointing at Redis DB 2.
         * Uses the same host/port/password as the primary connection factory but
         * targets a different logical database so AI cache keys are namespaced
         * independently.
         */
        @Bean("aiRedisConnectionFactory")
        public RedisConnectionFactory aiRedisConnectionFactory() {
            RedisStandaloneConfiguration cfg =
                    new RedisStandaloneConfiguration(redisHost, redisPort);
            cfg.setDatabase(aiCacheDatabase);
            if (StringUtils.hasText(redisPassword)) {
                cfg.setPassword(redisPassword);
            }
            return new LettuceConnectionFactory(cfg);
        }

        /**
         * Cache manager for AI responses — TTL 300 s, JSON serialisation with type info.
         * Named {@code aiResponseCacheManager} so {@code @Cacheable} annotations in
         * {@code AiInferenceService} can reference it explicitly.
         */
        @Bean("aiResponseCacheManager")
        public RedisCacheManager aiResponseCacheManager(
                @org.springframework.beans.factory.annotation.Qualifier("aiRedisConnectionFactory")
                RedisConnectionFactory factory) {

            RedisCacheConfiguration cfg = buildCacheConfig(AI_RESPONSE_TTL);
            return RedisCacheManager.builder(factory)
                    .cacheDefaults(cfg)
                    .withCacheConfiguration(CACHE_NAME, cfg)
                    .build();
        }

        private RedisCacheConfiguration buildCacheConfig(Duration ttl) {
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

    // ─── In-memory fallback (test profile / spring.cache.type != redis) ───────

    @Configuration
    @ConditionalOnMissingBean(name = "aiResponseCacheManager")
    static class FallbackAiCacheConfiguration {

        /**
         * Simple in-memory cache used when Redis is not available (e.g. unit tests
         * with {@code spring.cache.type=simple}). The {@code @Cacheable} annotation
         * becomes a functional no-op cache without side effects.
         */
        @Bean("aiResponseCacheManager")
        public CacheManager aiResponseFallbackCacheManager() {
            return new ConcurrentMapCacheManager(CACHE_NAME);
        }
    }
}
