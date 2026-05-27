package com.uip.backend.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for ForecastServiceAdapter — mocks Python REST responses.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForecastServiceAdapter — unit")
class ForecastServiceAdapterTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Test
    @DisplayName("Constructor creates RestClient with correct base URL")
    void constructor_setsBaseUrl() {
        String baseUrl = "http://uip-forecast-service:8090";
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        ForecastServiceAdapter adapter = new ForecastServiceAdapter(baseUrl, restClientBuilder);

        verify(restClientBuilder).baseUrl(baseUrl);
        verify(restClientBuilder).build();
    }

    @Test
    @DisplayName("forecast — successful response is mapped to ForecastResult")
    void forecast_success_mapsResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String responseBody = """
                {
                  "tenant_id": "hcm",
                  "building_id": "B1",
                  "model": "ARIMA",
                  "is_fallback": false,
                  "mape": 0.08,
                  "points": [
                    {
                      "timestamp": "2026-05-26T00:00:00Z",
                      "actual_value": null,
                      "predicted_value": 123.4,
                      "confidence_upper": 141.9,
                      "confidence_lower": 104.9,
                      "is_anomaly": false
                    }
                  ],
                  "generated_at": "2026-05-25T10:00:00Z"
                }
                """;

          server.expect(requestTo(allOf(
              containsString("/api/v1/forecast/energy"),
              containsString("building_id=B1"),
              containsString("horizon_days=30"),
              not(containsString("buildingId=")),
              not(containsString("horizonDays=")))))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ForecastServiceAdapter adapter = new ForecastServiceAdapter("http://forecast-service:8090", builder);

        ForecastResult result = adapter.forecast("hcm", "B1", 30);

        assertThat(result.tenantId()).isEqualTo("hcm");
        assertThat(result.buildingId()).isEqualTo("B1");
        assertThat(result.model()).isEqualTo("ARIMA");
        assertThat(result.isFallback()).isFalse();
        assertThat(result.mape()).isEqualTo(0.08);
        assertThat(result.points()).hasSize(1);
        assertThat(result.points().get(0).predictedValue()).isEqualTo(123.4);
        assertThat(result.generatedAt()).isEqualTo(Instant.parse("2026-05-25T10:00:00Z"));
        server.verify();
    }

    @Test
    @DisplayName("forecast — REST client failure throws ForecastServiceUnavailableException")
    void forecast_restClientFailure_throwsUnavailableException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

          server.expect(requestTo(allOf(
              containsString("/api/v1/forecast/energy"),
              containsString("building_id=B1"),
              containsString("horizon_days=30"),
              not(containsString("buildingId=")),
              not(containsString("horizonDays=")))))
              .andRespond(withServerError());

        ForecastServiceAdapter adapter = new ForecastServiceAdapter("http://forecast-service:8090", builder);

        assertThatThrownBy(() -> adapter.forecast("hcm", "B1", 30))
                .isInstanceOf(ForecastServiceUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("forecast — response with is_fallback=true maps correctly")
    void forecast_fallbackResponse_mapsIsFallback() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String responseBody = """
                {
                  "tenant_id": "hcm",
                  "building_id": "B2",
                  "model": "NAIVE",
                  "is_fallback": true,
                  "mape": null,
                  "points": [],
                  "generated_at": "2026-05-25T10:00:00Z"
                }
                """;

          server.expect(requestTo(allOf(
              containsString("/api/v1/forecast/energy"),
              containsString("building_id=B2"),
              containsString("horizon_days=7"),
              not(containsString("buildingId=")),
              not(containsString("horizonDays=")))))
              .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ForecastServiceAdapter adapter = new ForecastServiceAdapter("http://forecast-service:8090", builder);
        ForecastResult result = adapter.forecast("hcm", "B2", 7);

        assertThat(result.isFallback()).isTrue();
        assertThat(result.model()).isEqualTo("NAIVE");
        assertThat(result.mape()).isNull();
        assertThat(result.points()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("forecast — missing 'points' field returns insufficientData")
    void forecast_missingPoints_returnsInsufficientData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String responseBody = """
                {
                  "tenant_id": "hcm",
                  "building_id": "B1",
                  "model": "ARIMA",
                  "is_fallback": false,
                  "mape": 0.05
                }
                """;

          server.expect(requestTo(containsString("/api/v1/forecast/energy")))
              .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ForecastServiceAdapter adapter = new ForecastServiceAdapter("http://forecast-service:8090", builder);
        ForecastResult result = adapter.forecast("hcm", "B1", 7);

        assertThat(result.model()).isEqualTo("NONE");
        assertThat(result.isFallback()).isTrue();
        assertThat(result.points()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("forecast — missing 'generated_at' uses current time")
    void forecast_missingGeneratedAt_usesCurrentTime() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String responseBody = """
                {
                  "tenant_id": "hcm",
                  "building_id": "B1",
                  "model": "ARIMA",
                  "is_fallback": false,
                  "mape": 0.03,
                  "points": [
                    {
                      "timestamp": "2026-05-26T00:00:00Z",
                      "actual_value": null,
                      "predicted_value": 100.0,
                      "confidence_upper": 115.0,
                      "confidence_lower": 85.0,
                      "is_anomaly": false
                    }
                  ]
                }
                """;

          server.expect(requestTo(containsString("/api/v1/forecast/energy")))
              .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ForecastServiceAdapter adapter = new ForecastServiceAdapter("http://forecast-service:8090", builder);
        ForecastResult result = adapter.forecast("hcm", "B1", 1);

        assertThat(result.generatedAt()).isNotNull();
        assertThat(result.points()).hasSize(1);
        server.verify();
    }
}
