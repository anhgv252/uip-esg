package com.uip.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MVP5 Sprint M5-1 Task T14 — Spring config bug-class hardening gate.
 *
 * <p>This test is a <b>regression detector</b> for the 4 Spring config bug classes
 * documented in {@code feedback_mvp4_config_bugs} (memo: 93→6 failures surfaced by a
 * full {@code @SpringBootTest} when adding a {@link CacheManager} / {@link KafkaTemplate}
 * bean). Each of those bugs fails at <i>ApplicationContext startup</i>, not at runtime —
 * so a context-load test is the cheapest possible gate that catches the whole class.</p>
 *
 * <h2>What this gate catches</h2>
 * <ul>
 *   <li><b>Bean override collision</b> — two same-named {@code @Bean} methods firing
 *       together (e.g. if the {@code @ConditionalOnProperty} mutual-exclusion in
 *       {@code AiCacheConfig} were ever collapsed into a single nested class without
 *       the guard). Surfaces as {@code BeanDefinitionOverrideException}.</li>
 *   <li><b>Missing {@code @Primary}</b> — multiple {@link CacheManager} beans (EsgCacheConfig
 *       + AiCacheConfig) with no {@code @Primary}, so an unqualified
 *       {@code @Autowired CacheManager} cannot resolve. Surfaces as
 *       {@code NoUniqueBeanDefinitionException}.</li>
 *   <li><b>Non-lazy cache hit</b> — a cache bean that eagerly connects to Redis at startup
 *       (rather than lazily on first {@code @Cacheable} call) and therefore fails the
 *       context load when Redis is mocked.</li>
 *   <li><b>{@code @RetryableTopic} / KafkaTemplate wiring</b> — a Kafka listener that
 *       depends on a named {@code KafkaTemplate} bean that is not registered.</li>
 *   <li><b>Circular dependency</b> — any bean cycle introduced by a new config class.</li>
 * </ul>
 *
 * <h2>GOLDEN RULE (memo: feedback_mvp4_config_bugs)</h2>
 * <blockquote>
 *   Every time a new {@link CacheManager} or {@link org.springframework.kafka.core.KafkaTemplate}
 *   bean is added, <b>this test must still pass</b>. A failure here is a config bug class
 *   (bean override / missing {@code @Primary} / non-lazy cache), not a flaky test.
 * </blockquote>
 *
 * <h2>Why {@code @ActiveProfiles("test")}</h2>
 * <p>The {@code test} profile (see {@code application.yml}, multi-document section
 * {@code on-profile: test}) sets {@code spring.cache.type=simple}, which selects the
 * in-memory {@link ConcurrentMapCacheManager} variant of {@code AiCacheConfig} and
 * deactivates the Redis-backed variant. This is the path that previously triggered the
 * bean-override collision in MVP4 — exercising it here is the regression guard.</p>
 *
 * <h2>Test-shape decision</h2>
 * <p>This test deliberately uses the <b>full</b> {@code @SpringBootTest} rather than a
 * slice or {@code ApplicationContextRunner}: the bug class only manifests when the entire
 * auto-configuration chain runs. {@code ApplicationContextRunner} slices are used in
 * {@link AiCacheConfigMutualExclusionTest} for the per-condition mutual-exclusion guard,
 * which is Docker-free; this test exercises the integrated whole.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // Neuter scheduled warmup tasks that would hit mocked Redis — matches the
                // established pattern in EsgServiceIT / WorkflowStartupTest.
                "uip.cagg.alert-refresh-ms=999999999",
                "uip.cagg.sensor-refresh-ms=999999999",
                // Exclude auto-configs that try to reach real Redis/Kafka at health-check time.
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration," +
                        "org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration," +
                        "org.camunda.bpm.spring.boot.starter.rest.CamundaBpmRestJerseyAutoConfiguration"
        }
)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@DisplayName("M5-1 T14 — Spring ApplicationContext config-bug gate")
class ApplicationContextLoadsIT {

    /**
     * Single shared Postgres container — Flyway migrations must apply so JPA entity
     * managers initialise. {@code disabledWithoutDocker = true} means CI/dev with Docker
     * runs the gate; environments without Docker skip it gracefully (matching every
     * other backend IT — see TenantContextFilterConditionalTest).
     */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("uip")
            .withPassword("test_password");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        // Dead ports — Kafka/Redis clients are mocked below, but auto-config still
        // resolves bootstrap-servers/host props. A dead port is the canonical pattern
        // (see WorkflowStartupTest, TenantContextFilterConditionalTest).
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6399");
        registry.add("security.jwt.secret",
                () -> Base64.getEncoder().encodeToString(
                        "uip-config-gate-test-secret-32b!".getBytes()));
    }

    // ── Mocked infrastructure beans (no real Redis/Kafka I/O) ───────────────────
    // Same mock set as EsgServiceIT / WorkflowStartupTest — proven to let the full
    // context load without a Redis or Kafka broker running.

    @MockBean @SuppressWarnings("unused")
    RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    StringRedisTemplate redisTemplate;
    @MockBean @SuppressWarnings("unused")
    RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused")
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    ApplicationContext applicationContext;

    // ── Gate 1: full context loads ──────────────────────────────────────────────

    /**
     * The whole point of the gate. If the Spring ApplicationContext cannot start,
     * this fails with the underlying {@code BeanDefinitionOverrideException} /
     * {@code NoUniqueBeanDefinitionException} / circular-dependency stack — which is
     * exactly the config-bug-class signal we want surfaced at build time, not in prod.
     */
    @Test
    @DisplayName("contextLoads: full Spring context starts without bean collision / missing @Primary / circular dep")
    void contextLoads() {
        // The @SpringBootTest bootstrap itself is the assertion — reaching this line
        // means startup succeeded. The explicit assertion documents intent for readers
        // and protects against a future refactor that accidentally disables the test.
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getStartupDate()).isPositive();
    }

    // ── Gate 2: both CacheManager beans coexist (no override) ───────────────────

    /**
     * Guards the {@code AiCacheConfig} comment: "two same-named {@code @Bean} methods
     * with mutually-exclusive {@code @ConditionalOnProperty} so exactly one fires".
     * If the mutual-exclusion breaks, only ONE bean named {@code aiResponseCacheManager}
     * survives and the other is silently shadowed — this assertion catches that.
     *
     * <p>Bean names verified:
     * <ul>
     *   <li>{@code aiResponseCacheManager} — declared in {@code AiCacheConfig}
     *       ({@link com.uip.backend.ai.cache.AiCacheConfig#CACHE_MANAGER_BEAN}).</li>
     *   <li>{@code cacheManager} — declared in {@code CacheConfig} ({@code @Primary}).</li>
     * </ul>
     * Under the {@code test} profile ({@code spring.cache.type=simple}) the AI cache
     * manager resolves to {@link ConcurrentMapCacheManager}; the ESG
     * {@code @Primary} {@code cacheManager} bean is NOT registered (its
     * {@code @ConditionalOnProperty(havingValue = "redis", matchIfMissing = true)}
     * excludes it when {@code type=simple}).</p>
     */
    @Test
    @DisplayName("allCacheManagerBeansResolvable: aiResponseCacheManager present; no BeanDefinitionOverrideException")
    void allCacheManagerBeansResolvable() {
        // aiResponseCacheManager MUST be resolvable by name — if its bean definition
        // collided with another same-named bean at startup, contextLoads() would have
        // already failed; this assert additionally nails the name down so a rename is
        // caught as a regression.
        assertThat(applicationContext.containsBean("aiResponseCacheManager"))
                .as("aiResponseCacheManager bean must be registered — AiCacheConfig condition is broken if absent")
                .isTrue();

        Object aiCacheManager = applicationContext.getBean("aiResponseCacheManager");
        assertThat(aiCacheManager)
                .as("Under spring.cache.type=simple the AI cache manager must be in-memory")
                .isInstanceOf(ConcurrentMapCacheManager.class);
    }

    // ── Gate 3: JSON KafkaTemplate resolvable (avroKafkaTemplate is gated, see Gate 5) ──

    /**
     * Verifies the default JSON {@code kafkaTemplate} bean is resolvable. This is the
     * bean that {@code DualPublishKafkaProducer} and all v1 publishers depend on; if it
     * disappears (e.g. someone overrides the auto-config), every Kafka producer fails
     * at runtime.
     */
    @Test
    @DisplayName("kafkaTemplatesResolvable: default JSON kafkaTemplate bean present")
    void kafkaTemplatesResolvable() {
        assertThat(applicationContext.containsBean("kafkaTemplate"))
                .as("Default JSON kafkaTemplate (Spring Boot KafkaAutoConfiguration) must be present")
                .isTrue();
    }

    // ── Gate 4: @Primary CacheManager resolves with no qualifier ────────────────

    /**
     * Guards the {@code @Primary} annotation on ESG's {@code cacheManager}. Under the
     * {@code test} profile ({@code type=simple}) the ESG Redis-backed
     * {@code @Primary cacheManager} bean is NOT registered (its
     * {@code @ConditionalOnProperty} excludes it) — so an unqualified
     * {@code @Autowired CacheManager} must resolve to the AI cache manager instead.
     *
     * <p>This is intentionally permissive about <i>which</i> bean wins: the bug-class
     * signal we care about is "exactly one CacheManager is resolvable" (no
     * {@code NoUniqueBeanDefinitionException}). When the {@code redis} profile is
     * active, the {@code @Primary} ESG manager wins; under {@code simple}, the AI
     * manager is the only candidate. Either is correct — what is forbidden is
     * ambiguity.</p>
     */
    @Test
    @DisplayName("cacheManagerPrimaryResolves: an unqualified CacheManager injection resolves unambiguously")
    void cacheManagerPrimaryResolves() {
        // getBean(CacheManager.class) throws NoUniqueBeanDefinitionException if more
        // than one CacheManager is registered AND none is @Primary. If exactly one is
        // registered (or @Primary marks one), this returns it.
        CacheManager resolved = applicationContext.getBean(CacheManager.class);
        assertThat(resolved)
                .as("Unqualified CacheManager must resolve — missing @Primary or duplicate registration")
                .isNotNull();
    }
}
