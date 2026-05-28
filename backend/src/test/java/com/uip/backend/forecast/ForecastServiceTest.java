package com.uip.backend.forecast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ForecastService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForecastService — unit")
class ForecastServiceTest {

    @Mock
    private ForecastPort forecastPort;

    @Mock
    private NaiveForecastAdapter naiveFallback;

    private ForecastService forecastService;

    private static final ForecastResult STUB_RESULT = new ForecastResult(
            "hcm", "B1", "ARIMA", false, 0.08,
            Collections.emptyList(), Instant.now()
    );

    private static final ForecastResult NAIVE_RESULT = new ForecastResult(
            "hcm", "B1", "NAIVE", true, null,
            Collections.emptyList(), Instant.now()
    );

    @BeforeEach
    void setUp() {
        forecastService = new ForecastService(forecastPort, naiveFallback);
    }

    @Test
    @DisplayName("forecast — delegates to ForecastPort and returns result")
    void forecast_delegatesToPort() {
        when(forecastPort.forecast("hcm", "B1", 30)).thenReturn(STUB_RESULT);

        ForecastResult result = forecastService.forecast("hcm", "B1", 30);

        assertThat(result).isNotNull();
        assertThat(result.tenantId()).isEqualTo("hcm");
        assertThat(result.buildingId()).isEqualTo("B1");
        assertThat(result.model()).isEqualTo("ARIMA");
        assertThat(result.isFallback()).isFalse();
        verify(forecastPort, times(1)).forecast("hcm", "B1", 30);
        verifyNoInteractions(naiveFallback);
    }

    @Test
    @DisplayName("forecast — falls back to naive when primary port unavailable")
    void forecast_fallsBackToNaive() {
        when(forecastPort.forecast(anyString(), anyString(), anyInt()))
                .thenThrow(new ForecastServiceUnavailableException("Connection refused", null));
        when(naiveFallback.forecast("hcm", "B1", 30)).thenReturn(NAIVE_RESULT);

        ForecastResult result = forecastService.forecast("hcm", "B1", 30);

        assertThat(result).isNotNull();
        assertThat(result.model()).isEqualTo("NAIVE");
        assertThat(result.isFallback()).isTrue();
        verify(forecastPort, times(1)).forecast("hcm", "B1", 30);
        verify(naiveFallback, times(1)).forecast("hcm", "B1", 30);
    }

    @Test
    @DisplayName("forecast — propagates exception when fallback also fails")
    void forecast_propagatesExceptionWhenFallbackFails() {
        when(forecastPort.forecast(anyString(), anyString(), anyInt()))
                .thenThrow(new ForecastServiceUnavailableException("Port failed", null));
        when(naiveFallback.forecast(anyString(), anyString(), anyInt()))
                .thenThrow(new ForecastServiceUnavailableException("Naive failed", null));

        org.junit.jupiter.api.Assertions.assertThrows(
                ForecastServiceUnavailableException.class,
                () -> forecastService.forecast("hcm", "B1", 30)
        );
    }

    @Test
    @DisplayName("forecast — different tenants are correctly passed to port")
    void forecast_multiTenant() {
        ForecastResult hcmResult = new ForecastResult("hcm", "B1", "NAIVE", true, null,
                Collections.emptyList(), Instant.now());
        ForecastResult hnoiResult = new ForecastResult("hn", "B2", "NAIVE", true, null,
                Collections.emptyList(), Instant.now());

        when(forecastPort.forecast("hcm", "B1", 7)).thenReturn(hcmResult);
        when(forecastPort.forecast("hn", "B2", 14)).thenReturn(hnoiResult);

        assertThat(forecastService.forecast("hcm", "B1", 7).tenantId()).isEqualTo("hcm");
        assertThat(forecastService.forecast("hn", "B2", 14).tenantId()).isEqualTo("hn");
    }
}
