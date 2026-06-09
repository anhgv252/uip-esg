package com.uip.backend.esg.config.analytics;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer-side Pact test for analytics-service energy-aggregate contract.
 *
 * <p>Verifies that the backend (consumer) expectations match what
 * analytics-service (provider) actually returns.</p>
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "analytics-service", pactVersion = PactSpecVersion.V3)
@DisplayName("Pact Consumer — Analytics Service Energy Aggregate Contract")
class AnalyticsServiceConsumerPactTest {

    @Pact(consumer = "uip-backend")
    RequestResponsePact energyAggregatePact(PactDslWithProvider builder) {
        return builder
                .given("analytics data exists for tenant alpha")
                .uponReceiving("a POST request for energy aggregate")
                    .path("/energy-aggregate")
                    .method("POST")
                    .headers(Map.of("Content-Type", "application/json"))
                    .body("{\"tenantId\":\"alpha\",\"buildingIds\":[\"B01\"],"
                            + "\"fromEpoch\":1704067200,\"toEpoch\":1706745600}")
                .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body("{\"tenantId\":\"alpha\",\"fromEpoch\":1704067200,"
                            + "\"toEpoch\":1706745600,\"totalKwh\":15000.0,"
                            + "\"peakDemandKw\":120.5,\"averagePowerFactor\":0.95,"
                            + "\"buildings\":[{\"buildingId\":\"B01\","
                            + "\"totalKwh\":15000.0,\"peakDemandKw\":120.5}]}")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "energyAggregatePact")
    @DisplayName("Backend consumer expects 200 with energy aggregate data")
    void verifyEnergyAggregateContract(MockServer mockServer) {
        RestTemplate restTemplate = new RestTemplate();

        var request = new ClickHouseRestAnalyticsAdapter.EnergyAggregateHttpRequest(
                "alpha", java.util.List.of("B01"), 1704067200L, 1706745600L);

        ResponseEntity<ClickHouseRestAnalyticsAdapter.EnergyAggregateHttpResponse> response =
                restTemplate.postForEntity(
                        mockServer.getUrl() + "/energy-aggregate",
                        request,
                        ClickHouseRestAnalyticsAdapter.EnergyAggregateHttpResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalKwh()).isEqualTo(15000.0);
        assertThat(response.getBody().tenantId()).isEqualTo("alpha");
    }
}
