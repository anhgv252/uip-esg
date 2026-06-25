package com.uip.backend.config;

import com.uip.backend.kafka.producer.AvroProducerConfig;
import com.uip.backend.kafka.producer.DualPublishKafkaProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * MVP5 Sprint M5-1 Task T14 — Docker-free regression guard for the
 * Avro dual-publish conditional-bean chain.
 *
 * <p>The chain has two links:</p>
 * <ol>
 *   <li>{@link AvroProducerConfig} is gated by
 *       {@code @ConditionalOnProperty(name="apicurio.registry.enabled", havingValue="true",
 *       matchIfMissing=false)} — so the {@code avroKafkaTemplate} bean exists ONLY when
 *       the flag is explicitly set.</li>
 *   <li>{@link DualPublishKafkaProducer} is gated by
 *       {@code @ConditionalOnBean(name="avroKafkaTemplate")} — so it activates only when
 *       (1) fires.</li>
 * </ol>
 *
 * <p>This is a known sharp edge: the flag does NOT exist in {@code application.yml} by
 * default, so in the default profile neither bean loads. The {@link #disabledFlag_BothBeansAbsent}
 * test pins that fact down so a future change to {@code matchIfMissing=true} is caught as
 * a deliberate, reviewable regression rather than a silent behavior change.</p>
 *
 * <h2>GOLDEN RULE (memo: feedback_mvp4_config_bugs)</h2>
 * <blockquote>
 *   If you change {@code matchIfMissing} on {@code apicurio.registry.enabled} OR swap
 *   {@code DualPublishKafkaProducer}'s {@code @ConditionalOnBean} qualifier,
 *   <b>this test must still pass</b> — otherwise the dual-publish chain loads
 *   unexpectedly and may collide with another {@link KafkaTemplate} bean.
 * </blockquote>
 *
 * @see ApplicationContextLoadsIT Gate 3 — default {@code kafkaTemplate} resolvability
 */
@DisplayName("M5-1 T14 — AvroProducerConfig ⇄ DualPublishKafkaProducer conditional-bean chain")
class AvroProducerConditionalBeanTest {

    /**
     * Lightweight runner: loads Kafka auto-config + {@link AvroProducerConfig} +
     * {@link DualPublishKafkaProducer}. A stub default {@code kafkaTemplate} is provided
     * (mock) so {@link DualPublishKafkaProducer}'s {@code @Qualifier("kafkaTemplate")}
     * dependency resolves when the bean activates.
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    org.springframework.boot.autoconfigure.AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(AvroProducerConfig.class, DualPublishKafkaProducer.class)
            .withBean("kafkaTemplate", KafkaTemplate.class, () -> mock(KafkaTemplate.class));

    // ─── apicurio.registry.enabled absent (default) ────────────────────────────

    @Nested
    @DisplayName("apicurio.registry.enabled absent → both Avro beans absent (default profile)")
    class FlagAbsent {

        @Test
        @DisplayName("avroKafkaTemplate bean is NOT registered")
        void disabledFlag_avroTemplateAbsent() {
            runner.run(ctx ->
                    assertThat(ctx).doesNotHaveBean("avroKafkaTemplate"));
        }

        @Test
        @DisplayName("DualPublishKafkaProducer bean is NOT registered (no @ConditionalOnBean match)")
        void disabledFlag_dualPublishProducerAbsent() {
            runner.run(ctx ->
                    assertThat(ctx).doesNotHaveBean(DualPublishKafkaProducer.class));
        }
    }

    // ─── apicurio.registry.enabled=true ────────────────────────────────────────

    @Nested
    @DisplayName("apicurio.registry.enabled=true → both Avro beans present")
    class FlagEnabled {

        @Test
        @DisplayName("avroKafkaTemplate bean IS registered")
        void enabledFlag_avroTemplatePresent() {
            runner.withPropertyValues("apicurio.registry.enabled=true")
                    .run(ctx -> assertThat(ctx.containsBean("avroKafkaTemplate")).isTrue());
        }

        @Test
        @DisplayName("avroKafkaTemplate is typed as KafkaTemplate<String, GenericRecord>")
        void enabledFlag_avroTemplateCorrectType() {
            runner.withPropertyValues("apicurio.registry.enabled=true")
                    .run(ctx -> {
                        Object bean = ctx.getBean("avroKafkaTemplate");
                        // Type erasure means we can only assert on the raw type.
                        assertThat(bean).isInstanceOf(KafkaTemplate.class);
                    });
        }

        @Test
        @DisplayName("DualPublishKafkaProducer bean IS registered (ConditionalOnBean satisfied)")
        void enabledFlag_dualPublishProducerPresent() {
            runner.withPropertyValues("apicurio.registry.enabled=true")
                    .run(ctx -> assertThat(ctx).hasSingleBean(DualPublishKafkaProducer.class));
        }
    }

    // ─── apicurio.registry.enabled=false ───────────────────────────────────────

    @Nested
    @DisplayName("apicurio.registry.enabled=false → both Avro beans absent (explicit opt-out)")
    class FlagDisabled {

        @Test
        @DisplayName("avroKafkaTemplate bean is NOT registered")
        void explicitFalse_avroTemplateAbsent() {
            runner.withPropertyValues("apicurio.registry.enabled=false")
                    .run(ctx -> assertThat(ctx).doesNotHaveBean("avroKafkaTemplate"));
        }
    }
}
