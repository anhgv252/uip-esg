package com.uip.backend.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pact Provider Verification Test -- Backend Service.
 *
 * <p>Verifies that the backend service fulfills contracts defined by its consumers.
 * Loads Pact files from {@code src/test/resources/pacts/} and verifies each
 * interaction against the running Spring Boot test server.</p>
 *
 * <p>Contract verification flow:
 * <ol>
 *   <li>Consumer tests (e.g. frontend, other microservices) generate Pact files</li>
 *   <li>Pact files are copied to {@code src/test/resources/pacts/}</li>
 *   <li>This test loads Pact files and verifies provider responses</li>
 * </ol>
 *
 * <p>Run manually: {@code scripts/pact-verify.sh}</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Provider("backend")
@PactFolder("pacts")
@Tag("contract")
@Tag("integration")
@DisplayName("Pact Provider Verification -- Backend")
class BackendProviderPactTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    @DisplayName("Verify all consumer contracts")
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    // -- Provider States -------------------------------------------------------

    @State("backend has ESG data for tenant hcm")
    void setUpEsgDataForTenant() {
        // In test profile, repositories return seeded data via Testcontainers.
        // No additional setup needed.
    }

    @State("backend has sensor data available")
    void setUpSensorData() {
        // In test profile, repositories return seeded data via Testcontainers.
        // No additional setup needed.
    }

    @State("backend has alert rules configured")
    void setUpAlertRules() {
        // In test profile, repositories return seeded data via Testcontainers.
        // No additional setup needed.
    }
}
