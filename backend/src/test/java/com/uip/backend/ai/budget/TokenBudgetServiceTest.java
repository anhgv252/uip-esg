package com.uip.backend.ai.budget;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Unit tests for {@link TokenBudgetService}.
 *
 * Uses {@link ReflectionTestUtils} to inject @Value fields without a Spring context.
 */
@DisplayName("TokenBudgetService")
class TokenBudgetServiceTest {

    private static final long   MONTHLY_LIMIT     = 1_000_000L;
    private static final double ALERT_THRESHOLD   = 0.8;

    private TokenBudgetService service;

    @BeforeEach
    void setUp() {
        service = new TokenBudgetService();
        ReflectionTestUtils.setField(service, "monthlyLimit",    MONTHLY_LIMIT);
        ReflectionTestUtils.setField(service, "alertThreshold",  ALERT_THRESHOLD);
    }

    // ─── isWithinBudget ───────────────────────────────────────────────────────

    @Test
    @DisplayName("isWithinBudget: 0 tokens → within budget")
    void isWithinBudget_zeroTokens_true() {
        assertThat(service.isWithinBudget(0L)).isTrue();
    }

    @Test
    @DisplayName("isWithinBudget: tokens < limit → within budget")
    void isWithinBudget_belowLimit_true() {
        assertThat(service.isWithinBudget(999_999L)).isTrue();
    }

    @Test
    @DisplayName("isWithinBudget: tokens == limit → NOT within budget (boundary)")
    void isWithinBudget_exactlyAtLimit_false() {
        assertThat(service.isWithinBudget(MONTHLY_LIMIT)).isFalse();
    }

    @Test
    @DisplayName("isWithinBudget: tokens > limit → NOT within budget")
    void isWithinBudget_exceedsLimit_false() {
        assertThat(service.isWithinBudget(1_500_000L)).isFalse();
    }

    // ─── isApproachingLimit ───────────────────────────────────────────────────

    @Test
    @DisplayName("isApproachingLimit: tokens at threshold (80%) → approaching")
    void isApproachingLimit_atThreshold_true() {
        long at80Percent = (long) (MONTHLY_LIMIT * ALERT_THRESHOLD);  // 800_000
        assertThat(service.isApproachingLimit(at80Percent)).isTrue();
    }

    @Test
    @DisplayName("isApproachingLimit: tokens above threshold → approaching")
    void isApproachingLimit_aboveThreshold_true() {
        assertThat(service.isApproachingLimit(900_000L)).isTrue();
    }

    @Test
    @DisplayName("isApproachingLimit: tokens below threshold → not approaching")
    void isApproachingLimit_belowThreshold_false() {
        assertThat(service.isApproachingLimit(500_000L)).isFalse();
    }

    @Test
    @DisplayName("isApproachingLimit: tokens just below threshold (79.9%) → not approaching")
    void isApproachingLimit_justBelowThreshold_false() {
        assertThat(service.isApproachingLimit(799_999L)).isFalse();
    }

    // ─── getBudgetUtilization ─────────────────────────────────────────────────

    @Test
    @DisplayName("getBudgetUtilization: 0 tokens → 0.0")
    void getBudgetUtilization_zero_returnsZero() {
        assertThat(service.getBudgetUtilization(0L)).isCloseTo(0.0, offset(1e-9));
    }

    @Test
    @DisplayName("getBudgetUtilization: 500_000 tokens → 0.5")
    void getBudgetUtilization_half_returns0_5() {
        assertThat(service.getBudgetUtilization(500_000L)).isCloseTo(0.5, offset(1e-9));
    }

    @Test
    @DisplayName("getBudgetUtilization: 1_000_000 tokens → 1.0 (100%)")
    void getBudgetUtilization_full_returns1_0() {
        assertThat(service.getBudgetUtilization(1_000_000L)).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    @DisplayName("getBudgetUtilization: 1_500_000 tokens → 1.5 (150%, over budget)")
    void getBudgetUtilization_overBudget_returns1_5() {
        assertThat(service.getBudgetUtilization(1_500_000L)).isCloseTo(1.5, offset(1e-9));
    }

    @Test
    @DisplayName("getBudgetUtilization: 800_000 tokens → 0.8 (alert threshold)")
    void getBudgetUtilization_atAlertThreshold_returns0_8() {
        assertThat(service.getBudgetUtilization(800_000L)).isCloseTo(0.8, offset(1e-9));
    }
}
