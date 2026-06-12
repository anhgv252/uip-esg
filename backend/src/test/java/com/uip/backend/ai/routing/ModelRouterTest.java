package com.uip.backend.ai.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ModelRouter}.
 */
@DisplayName("ModelRouter")
class ModelRouterTest {

    private static final String HAIKU  = "claude-haiku-4-5-20251001";
    private static final String SONNET = "claude-sonnet-4-6";

    private final ModelRouter router = new ModelRouter();

    @Test
    @DisplayName("LOW priority → Haiku regardless of token count")
    void lowPriority_returnsHaiku() {
        assertThat(router.selectModel(10_000, "LOW")).isEqualTo(HAIKU);
    }

    @Test
    @DisplayName("LOW priority with zero tokens → Haiku")
    void lowPriority_zeroTokens_returnsHaiku() {
        assertThat(router.selectModel(0, "LOW")).isEqualTo(HAIKU);
    }

    @Test
    @DisplayName("HIGH priority + large tokens (>500) → Sonnet")
    void highPriorityLargeTokens_returnsSonnet() {
        assertThat(router.selectModel(1000, "HIGH")).isEqualTo(SONNET);
    }

    @Test
    @DisplayName("MEDIUM priority + large tokens → Sonnet")
    void mediumPriorityLargeTokens_returnsSonnet() {
        assertThat(router.selectModel(501, "MEDIUM")).isEqualTo(SONNET);
    }

    @Test
    @DisplayName("LOW tokens (≤500) → Haiku regardless of MEDIUM priority")
    void lowTokens_returnsHaikuRegardlessOfMediumPriority() {
        assertThat(router.selectModel(500, "MEDIUM")).isEqualTo(HAIKU);
    }

    @Test
    @DisplayName("exactly 500 tokens → Haiku (boundary)")
    void exactlyHaikuMaxTokens_returnsHaiku() {
        assertThat(router.selectModel(500, "HIGH")).isEqualTo(HAIKU);
    }

    @Test
    @DisplayName("501 tokens + HIGH priority → Sonnet (boundary)")
    void oneOverHaikuMaxTokens_returnsSonnet() {
        assertThat(router.selectModel(501, "HIGH")).isEqualTo(SONNET);
    }

    @Test
    @DisplayName("HIGH priority + small tokens → Haiku (cost-optimized for short requests)")
    void highPrioritySmallTokens_returnsHaiku() {
        assertThat(router.selectModel(100, "HIGH")).isEqualTo(HAIKU);
    }

    @Test
    @DisplayName("null priority treated as non-LOW → uses token threshold")
    void nullPriority_usesTokenThreshold() {
        // null != "LOW" → falls back to token-based routing
        assertThat(router.selectModel(200, null)).isEqualTo(HAIKU);   // ≤500 → Haiku
        assertThat(router.selectModel(1000, null)).isEqualTo(SONNET); // >500 → Sonnet
    }
}
