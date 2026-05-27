package com.uip.backend.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for DisabledForecastAdapter.
 * Verifies that it throws ForecastServiceUnavailableException for all calls (ADR-032).
 */
@DisplayName("DisabledForecastAdapter — unit")
class DisabledForecastAdapterTest {

    private final DisabledForecastAdapter adapter = new DisabledForecastAdapter();

    @Test
    @DisplayName("forecast — throws ForecastServiceUnavailableException when engine disabled")
    void forecast_throws_unavailableException() {
        assertThatThrownBy(() -> adapter.forecast("hcm", "B1", 30))
                .isInstanceOf(ForecastServiceUnavailableException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    @DisplayName("forecast — exception message contains uip.capabilities.forecast-engine")
    void forecast_exceptionMessage_containsPropertyName() {
        assertThatThrownBy(() -> adapter.forecast("any-tenant", "any-building", 7))
                .isInstanceOf(ForecastServiceUnavailableException.class)
                .hasMessageContaining("uip.capabilities.forecast-engine");
    }

    @Test
    @DisplayName("forecast — cause is null (not wrapping another exception)")
    void forecast_exception_causeIsNull() {
        try {
            adapter.forecast("hcm", "B1", 30);
        } catch (ForecastServiceUnavailableException ex) {
            assertNotNull(ex.getMessage());
            // Disabled adapter sets cause=null
            org.junit.jupiter.api.Assertions.assertNull(ex.getCause());
        }
    }
}
