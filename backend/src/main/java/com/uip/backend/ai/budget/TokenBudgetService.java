package com.uip.backend.ai.budget;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * M4-AI-05: Tracks AI token budget and provides guardrails for monthly spend control.
 *
 * <p>Callers query this service before dispatching inference requests to avoid
 * exceeding the monthly token quota. When approaching the limit, alerts can be
 * triggered via the notification pipeline.</p>
 */
@Service
@Slf4j
public class TokenBudgetService {

    @Value("${ai.token-budget.monthly-limit:1000000}")
    private long monthlyLimit;

    @Value("${ai.token-budget.alert-threshold:0.8}")
    private double alertThreshold;

    /**
     * Returns {@code true} when the used token count is strictly below the monthly limit.
     *
     * @param tokensUsed cumulative tokens used in the current billing month
     */
    public boolean isWithinBudget(long tokensUsed) {
        boolean within = tokensUsed < monthlyLimit;
        if (!within) {
            log.warn("[TokenBudget] Monthly limit reached: used={} limit={}", tokensUsed, monthlyLimit);
        }
        return within;
    }

    /**
     * Returns {@code true} when token utilization has reached or exceeded the alert threshold.
     *
     * @param tokensUsed cumulative tokens used in the current billing month
     */
    public boolean isApproachingLimit(long tokensUsed) {
        double utilization = getBudgetUtilization(tokensUsed);
        boolean approaching = utilization >= alertThreshold;
        if (approaching) {
            log.info("[TokenBudget] Approaching monthly limit: utilization={} threshold={}",
                    String.format("%.1f%%", utilization * 100), String.format("%.1f%%", alertThreshold * 100));
        }
        return approaching;
    }

    /**
     * Returns the fraction of the monthly budget consumed (0.0 – unbounded).
     *
     * @param tokensUsed cumulative tokens used in the current billing month
     * @return ratio in range [0.0, ∞) — values &gt;1.0 indicate budget exceeded
     */
    public double getBudgetUtilization(long tokensUsed) {
        return (double) tokensUsed / monthlyLimit;
    }
}
