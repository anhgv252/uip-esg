package com.uip.backend.config;

import com.uip.backend.ai.cache.AiCacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MVP5 Sprint M5-1 Task T14 — Docker-free regression guard for the
 * {@code @ConditionalOnProperty} mutual-exclusion logic in {@link AiCacheConfig}.
 *
 * <p>{@code AiCacheConfig} declares <b>three</b> bean methods all named
 * {@code aiResponseCacheManager}, each guarded by a different value of
 * {@code spring.cache.type} ({@code redis}, {@code simple}, {@code none}). The class
 * Javadoc explicitly warns:</p>
 *
 * <blockquote>
 *   "Both bean methods live in this class but carry mutually-exclusive
 *    {@code @ConditionalOnProperty} so exactly one fires — this avoids the
 *    {@code BeanDefinitionOverrideException} that two same-named {@code @Bean} methods
 *    in separate nested classes would trigger."
 * </blockquote>
 *
 * <p>This test class asserts the mutual-exclusion <b>actually</b> holds — if a future
 * refactor collapses the conditions, widens a {@code havingValue}, or drops a
 * {@code matchIfMissing}, one of these tests fails immediately. Docker-free and sub-second
 * because {@link ApplicationContextRunner} evaluates conditions without booting the full
 * application context.</p>
 *
 * <h2>GOLDEN RULE (memo: feedback_mvp4_config_bugs)</h2>
 * <blockquote>
 *   If you change any {@code @ConditionalOnProperty} on a CacheManager/KafkaTemplate
 *   bean, <b>this test must still pass</b>. A failure means the mutual-exclusion is
 *   broken — fix the condition before merging, or you will reintroduce the
 *   {@code BeanDefinitionOverrideException} (MVP4: 93→6 failures).
 * </blockquote>
 *
 * @see ApplicationContextLoadsIT for the integrated full-context load gate
 */
@DisplayName("M5-1 T14 — AiCacheConfig @ConditionalOnProperty mutual-exclusion")
class AiCacheConfigMutualExclusionTest {

    /**
     * Lightweight runner: loads only {@link AiCacheConfig} — no DB, no Kafka, no Redis
     * auto-config. Each test sets one value of {@code spring.cache.type} and asserts
     * which bean variant fired (or that none fired).
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiCacheConfig.class);

    // ─── spring.cache.type=simple ───────────────────────────────────────────────

    @Nested
    @DisplayName("spring.cache.type=simple → in-memory ConcurrentMapCacheManager")
    class SimpleProfile {

        @Test
        @DisplayName("aiResponseCacheManager bean is registered")
        void simple_cacheManagerBeanRegistered() {
            runner.withPropertyValues("spring.cache.type=simple")
                    .run(ctx -> assertThat(ctx.containsBean("aiResponseCacheManager")).isTrue());
        }

        @Test
        @DisplayName("aiResponseCacheManager is a ConcurrentMapCacheManager (not Redis)")
        void simple_resolvesToConcurrentMapCacheManager() {
            runner.withPropertyValues("spring.cache.type=simple")
                    .run(ctx -> {
                        CacheManager cm = ctx.getBean("aiResponseCacheManager", CacheManager.class);
                        assertThat(cm).isInstanceOf(ConcurrentMapCacheManager.class);
                    });
        }

        @Test
        @DisplayName("Redis-backed variant is NOT registered (no BeanDefinitionOverrideException)")
        void simple_redisVariantNotLoaded() {
            runner.withPropertyValues("spring.cache.type=simple")
                    .run(ctx -> assertThat(ctx.getBeansOfType(RedisCacheManager.class)).isEmpty());
        }
    }

    // ─── spring.cache.type=redis ────────────────────────────────────────────────

    @Nested
    @DisplayName("spring.cache.type=redis → Redis-backed CacheManager")
    class RedisProfile {

        @Test
        @DisplayName("aiResponseCacheManager bean is registered (redis condition fires)")
        void redis_cacheManagerBeanRegistered() {
            runner.withPropertyValues("spring.cache.type=redis").run(ctx -> {
                // AiCacheConfig's own aiRedisConnectionFactory bean (also redis-gated)
                // supplies the connection factory; no external stub needed.
                assertThat(ctx.containsBean("aiResponseCacheManager")).isTrue();
                assertThat(ctx.containsBean("aiRedisConnectionFactory")).isTrue();
            });
        }
    }

    // ─── spring.cache.type=none ─────────────────────────────────────────────────

    @Nested
    @DisplayName("spring.cache.type=none → no-op ConcurrentMapCacheManager fallback")
    class NoneProfile {

        @Test
        @DisplayName("aiResponseCacheManager bean is registered (no-op variant)")
        void none_cacheManagerBeanRegistered() {
            runner.withPropertyValues("spring.cache.type=none")
                    .run(ctx -> assertThat(ctx.containsBean("aiResponseCacheManager")).isTrue());
        }

        @Test
        @DisplayName("aiResponseCacheManager is a ConcurrentMapCacheManager (no-op, semantically disabled)")
        void none_resolvesToConcurrentMapCacheManager() {
            runner.withPropertyValues("spring.cache.type=none")
                    .run(ctx -> {
                        CacheManager cm = ctx.getBean("aiResponseCacheManager", CacheManager.class);
                        assertThat(cm).isInstanceOf(ConcurrentMapCacheManager.class);
                    });
        }
    }

    // ─── spring.cache.type unspecified ──────────────────────────────────────────

    @Nested
    @DisplayName("spring.cache.type absent → redis matchIfMissing fires (production default)")
    class DefaultProfile {

        @Test
        @DisplayName("aiResponseCacheManager bean is registered when property is absent")
        void default_cacheManagerBeanRegistered() {
            // matchIfMissing=true on the redis variant → this is the production path.
            runner.run(ctx -> assertThat(ctx.containsBean("aiResponseCacheManager")).isTrue());
        }
    }

    // ─── bug-class sweep: every supported value must produce a clean context ────

    /**
     * Sanity sweep across the three supported values: none of them should cause a
     * startup failure. If mutual-exclusion ever breaks, ApplicationContextRunner surfaces
     * the {@code BeanDefinitionOverrideException} as a failed context here, AND at least
     * one of the per-profile tests above will fail first with a sharper signal.
     */
    @Test
    @DisplayName("[bug-class guard] every value of spring.cache.type loads without startup failure")
    void noProfileValueCausesStartupFailure() {
        for (String value : new String[]{"simple", "none"}) {
            runner.withPropertyValues("spring.cache.type=" + value)
                    .run(ctx -> assertThat(ctx.containsBean("aiResponseCacheManager"))
                            .as("aiResponseCacheManager must register under spring.cache.type=" + value)
                            .isTrue());
        }
        // redis variant is covered by RedisProfile.redis_cacheManagerBeanRegistered above.
    }
}
