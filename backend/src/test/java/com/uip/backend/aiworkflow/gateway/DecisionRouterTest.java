package com.uip.backend.aiworkflow.gateway;

import com.uip.backend.aiworkflow.gateway.DecisionRouter.RoutingAction;
import com.uip.backend.workflow.dto.AIDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for DecisionRouter — confidence-based routing.
 * 5 tests covering all 3 routing thresholds + boundary cases.
 */
@DisplayName("DecisionRouter — unit")
class DecisionRouterTest {

    private final DecisionRouter router = new DecisionRouter(mock(org.springframework.data.redis.core.StringRedisTemplate.class));

    private AIDecision decision(double confidence) {
        AIDecision d = new AIDecision();
        d.setDecision("TEST_DECISION");
        d.setConfidence(confidence);
        d.setReasoning("Test reasoning");
        d.setSeverity("MEDIUM");
        return d;
    }

    @Test
    @DisplayName("confidence 0.95 → AUTO_EXECUTE")
    void highConfidence_autoExecute() {
        var result = router.route(decision(0.95));
        assertThat(result.action()).isEqualTo(RoutingAction.AUTO_EXECUTE);
        assertThat(result.cached()).isFalse();
    }

    @Test
    @DisplayName("confidence 0.75 → OPERATOR_QUEUE")
    void mediumConfidence_operatorQueue() {
        var result = router.route(decision(0.75));
        assertThat(result.action()).isEqualTo(RoutingAction.OPERATOR_QUEUE);
    }

    @Test
    @DisplayName("confidence 0.5 → ESCALATE")
    void lowConfidence_escalate() {
        var result = router.route(decision(0.5));
        assertThat(result.action()).isEqualTo(RoutingAction.ESCALATE);
    }

    @Test
    @DisplayName("confidence exactly 0.85 → OPERATOR_QUEUE (boundary: > not >=)")
    void boundary85_operatorQueue() {
        // 0.85 is NOT > 0.85, so it falls to OPERATOR_QUEUE
        var result = router.route(decision(0.85));
        assertThat(result.action()).isEqualTo(RoutingAction.OPERATOR_QUEUE);
    }

    @Test
    @DisplayName("confidence exactly 0.6 → OPERATOR_QUEUE (boundary: >=)")
    void boundary60_operatorQueue() {
        // 0.6 >= 0.6, so OPERATOR_QUEUE
        var result = router.route(decision(0.6));
        assertThat(result.action()).isEqualTo(RoutingAction.OPERATOR_QUEUE);
    }
}
