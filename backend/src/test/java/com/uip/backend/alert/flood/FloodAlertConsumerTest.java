package com.uip.backend.alert.flood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FloodAlertConsumer — severity mapping.
 */
@DisplayName("FloodAlertConsumer — unit")
class FloodAlertConsumerTest {

    @Test
    @DisplayName("mapSeverity — P0_EMERGENCY → CRITICAL")
    void p0ToCritical() {
        assertThat(FloodAlertConsumer.mapSeverity("P0_EMERGENCY")).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("mapSeverity — P1_WARNING → HIGH")
    void p1ToHigh() {
        assertThat(FloodAlertConsumer.mapSeverity("P1_WARNING")).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("mapSeverity — P2_ADVISORY → WARNING")
    void p2ToWarning() {
        assertThat(FloodAlertConsumer.mapSeverity("P2_ADVISORY")).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("mapSeverity — null → WARNING (fallback)")
    void nullToWarning() {
        assertThat(FloodAlertConsumer.mapSeverity(null)).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("mapSeverity — unknown value → WARNING (fallback)")
    void unknownToWarning() {
        assertThat(FloodAlertConsumer.mapSeverity("P3_CUSTOM")).isEqualTo("WARNING");
    }
}
