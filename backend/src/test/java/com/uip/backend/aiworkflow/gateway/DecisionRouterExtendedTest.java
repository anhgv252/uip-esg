package com.uip.backend.aiworkflow.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * QA-2: DecisionRouter extended tests — 9 scenarios.
 * Tests confidence routing boundaries, fallback, cache behavior.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DecisionRouter — Extended QA Tests")
class DecisionRouterExtendedTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private DecisionRouter router;

    private AiDecisionInput createInput(double confidence, String decision, String reasoning) {
        AiDecisionInput input = new AiDecisionInput();
        input.setConfidence(confidence);
        input.setDecision(decision);
        input.setReasoning(reasoning);
        input.setSeverity("HIGH");
        return input;
    }

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        router = new DecisionRouter(redisTemplate);
    }

    @Nested
    @DisplayName("Confidence Routing")
    class ConfidenceRouting {

        @Test
        @DisplayName("DN-EXT-01: confidence 0.90 → AUTO_EXECUTE")
        void highConfidence_autoExecute() {
            var result = router.route(createInput(0.90, "EVACUATE", "High flood risk"));
            assertEquals(DecisionRouter.RoutingAction.AUTO_EXECUTE, result.action());
            assertFalse(result.cached());
        }

        @Test
        @DisplayName("DN-EXT-02: confidence 0.75 → OPERATOR_QUEUE")
        void mediumConfidence_operatorQueue() {
            var result = router.route(createInput(0.75, "MONITOR", "Moderate risk"));
            assertEquals(DecisionRouter.RoutingAction.OPERATOR_QUEUE, result.action());
        }

        @Test
        @DisplayName("DN-EXT-03: confidence 0.40 → ESCALATE")
        void lowConfidence_escalate() {
            var result = router.route(createInput(0.40, "UNCERTAIN", "Cannot determine"));
            assertEquals(DecisionRouter.RoutingAction.ESCALATE, result.action());
        }

        @Test
        @DisplayName("DN-EXT-04: confidence exactly 0.85 → OPERATOR_QUEUE (boundary, strict >)")
        void boundary85_operatorQueue() {
            // classify uses strict > for AUTO_EXECUTE, so 0.85 falls to OPERATOR_QUEUE
            var result = router.route(createInput(0.85, "EVACUATE", "Threshold risk"));
            assertEquals(DecisionRouter.RoutingAction.OPERATOR_QUEUE, result.action());
        }

        @Test
        @DisplayName("DN-EXT-04b: confidence 0.86 → AUTO_EXECUTE (just above boundary)")
        void justAbove85_autoExecute() {
            var result = router.route(createInput(0.86, "EVACUATE", "Just above threshold"));
            assertEquals(DecisionRouter.RoutingAction.AUTO_EXECUTE, result.action());
        }

        @Test
        @DisplayName("DN-EXT-05: confidence exactly 0.60 → OPERATOR_QUEUE (boundary)")
        void boundary60_operatorQueue() {
            var result = router.route(createInput(0.60, "MONITOR", "Low confidence"));
            assertEquals(DecisionRouter.RoutingAction.OPERATOR_QUEUE, result.action());
        }

        @Test
        @DisplayName("DN-EXT-06: confidence 0.59 → ESCALATE (just below boundary)")
        void belowBoundary60_escalate() {
            var result = router.route(createInput(0.59, "UNCERTAIN", "Very low"));
            assertEquals(DecisionRouter.RoutingAction.ESCALATE, result.action());
        }
    }

    @Nested
    @DisplayName("Cache Behavior")
    class CacheBehavior {

        @Test
        @DisplayName("DN-EXT-07: cache hit returns cached result, cached=true")
        void cacheHit_returnsCachedResult() {
            String cachedJson = "{\"action\":\"AUTO_EXECUTE\",\"decision\":\"EVACUATE\",\"reasoning\":\"Cached\",\"confidence\":0.9}";
            when(valueOps.get(anyString())).thenReturn(cachedJson);

            var result = router.routeWithCache("flood-risk", "context-hash", createInput(0.50, "TEST", "Test"));

            assertTrue(result.cached());
            assertEquals(DecisionRouter.RoutingAction.AUTO_EXECUTE, result.action());
            assertEquals("Cached", result.reasoning());
        }

        @Test
        @DisplayName("DN-EXT-08: cache miss routes normally, cached=false")
        void cacheMiss_routesNormally() {
            when(valueOps.get(anyString())).thenReturn(null);

            var result = router.routeWithCache("flood-risk", "context-hash", createInput(0.90, "EVACUATE", "High"));

            assertFalse(result.cached());
            assertEquals(DecisionRouter.RoutingAction.AUTO_EXECUTE, result.action());
        }

        @Test
        @DisplayName("DN-EXT-09: cache write succeeds for high confidence")
        void cacheWrite_succeedsForHighConfidence() {
            when(valueOps.get(anyString())).thenReturn(null);

            router.routeWithCache("flood-risk", "context-hash", createInput(0.90, "EVACUATE", "High"));

            verify(valueOps).set(anyString(), anyString(), any());
        }
    }
}
