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
 * Pact Consumer Test -- Backend as consumer of Notification Service.
 *
 * <p>Defines the contract that backend expects from notification-service REST API.
 * Pact files are generated in {@code build/pacts/} and used by the
 * notification-service provider verification test.</p>
 *
 * <p>Endpoints under test:
 * <ul>
 *   <li>POST /api/v1/notifications/send -- send a notification</li>
 *   <li>GET /api/v1/notifications/delivery-status/{id} -- check delivery status</li>
 * </ul>
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "notification-service")
@Tag("contract")
@DisplayName("Pact Consumer -- Backend to Notification Service")
class NotificationServiceConsumerPactTest {

    @Pact(consumer = "backend")
    V4Pact sendNotification(PactDslWithProvider builder) {
        return builder
            .given("notification service is available")
            .uponReceiving("a request to send notification")
                .path("/api/v1/notifications/send")
                .method("POST")
                .headers(Map.of(
                    "Content-Type", "application/json",
                    "Authorization", "Bearer test-token"))
                .body(new PactDslJsonBody()
                    .stringType("recipient", "user-001")
                    .stringType("channel", "PUSH")
                    .stringType("title", "Air Quality Alert")
                    .stringType("body", "AQI has exceeded 200 in your area")
                    .stringType("severity", "P1_WARNING"))
            .willRespondWith()
                .status(202)
                .body(new PactDslJsonBody()
                    .uuid("id")
                    .stringType("status", "ACCEPTED")
                    .stringType("channel", "PUSH"))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "sendNotification")
    @DisplayName("POST /api/v1/notifications/send returns 202 with acceptance")
    void verifySendNotification(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String json = """
            {
              "recipient": "user-001",
              "channel": "PUSH",
              "title": "Air Quality Alert",
              "body": "AQI has exceeded 200 in your area",
              "severity": "P1_WARNING"
            }
            """;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(mockServer.getUrl() + "/api/v1/notifications/send"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer test-token")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).contains("ACCEPTED");
    }

    private static final String TEST_NOTIFICATION_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Pact(consumer = "backend")
    V4Pact deliveryStatus(PactDslWithProvider builder) {
        return builder
            .given("notification delivery status exists")
            .uponReceiving("a request for delivery status")
                .path("/api/v1/notifications/delivery-status/" + TEST_NOTIFICATION_ID)
                .method("GET")
                .headers(Map.of("Authorization", "Bearer test-token"))
            .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                    .uuid("id")
                    .stringType("status", "DELIVERED")
                    .stringType("channel", "PUSH")
                    .datetime("deliveredAt", "yyyy-MM-dd'T'HH:mm:ss'Z'"))
            .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "deliveryStatus")
    @DisplayName("GET /api/v1/notifications/delivery-status/{id} returns 200 with status")
    void verifyDeliveryStatus(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(mockServer.getUrl()
                + "/api/v1/notifications/delivery-status/" + TEST_NOTIFICATION_ID))
            .header("Authorization", "Bearer test-token")
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("DELIVERED");
    }
}
