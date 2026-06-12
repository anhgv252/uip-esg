package com.uip.backend.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pact Consumer Test -- Backend as consumer of Analytics Service.
 *
 * <p>Defines the contract that backend expects from analytics-service REST API.
 * Pact files are generated in {@code build/pacts/} and used by the
 * analytics-service provider verification test.</p>
 *
 * <p>Endpoints under test:
 * <ul>
 *   <li>GET /api/v1/analytics/energy/aggregated -- energy aggregation query</li>
 *   <li>GET /api/v1/analytics/summary -- tenant analytics summary</li>
 * </ul>
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "analytics-service")
@Tag("contract")
@DisplayName("Pact Consumer -- Backend to Analytics Service")
class AnalyticsServiceConsumerPactTest {

    @Pact(consumer = "backend")
    V4Pact energyAggregation(PactDslWithProvider builder) {
        return builder
            .given("analytics data exists for tenant alpha")
            .uponReceiving("a request for energy aggregation")
                .path("/api/v1/analytics/energy/aggregated")
                .method("GET")
                .query("building=B1&from=2026-01-01T00:00:00Z&to=2026-03-31T23:59:59Z")
                .headers(Map.of("Authorization", "Bearer test-token"))
            .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                    .numberType("totalEnergyKwh", 12500.0)
                    .numberType("avgDailyKwh", 138.89)
                    .stringType("building", "B1")
                    .stringType("unit", "kWh"))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "energyAggregation")
    @DisplayName("GET /api/v1/analytics/energy/aggregated returns 200 with energy data")
    void verifyEnergyAggregation(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(mockServer.getUrl()
                + "/api/v1/analytics/energy/aggregated"
                + "?building=B1&from=2026-01-01T00:00:00Z&to=2026-03-31T23:59:59Z"))
            .header("Authorization", "Bearer test-token")
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("totalEnergyKwh");
        assertThat(response.body()).contains("building");
        assertThat(response.body()).contains("kWh");
    }

    @Pact(consumer = "backend")
    V4Pact analyticsSummary(PactDslWithProvider builder) {
        return builder
            .given("analytics data exists for tenant alpha")
            .uponReceiving("a request for analytics summary")
                .path("/api/v1/analytics/summary")
                .method("GET")
                .query("tenantId=hcm")
                .headers(Map.of("Authorization", "Bearer test-token"))
            .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                    .stringType("tenantId", "hcm")
                    .numberType("totalSensors", 150)
                    .numberType("activeSensors", 142)
                    .numberType("dataPointsLast24h", 50000L)
                    .stringType("status", "HEALTHY"))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "analyticsSummary")
    @DisplayName("GET /api/v1/analytics/summary returns 200 with tenant summary")
    void verifyAnalyticsSummary(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(mockServer.getUrl()
                + "/api/v1/analytics/summary?tenantId=hcm"))
            .header("Authorization", "Bearer test-token")
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("tenantId");
        assertThat(response.body()).contains("HEALTHY");
    }
}
