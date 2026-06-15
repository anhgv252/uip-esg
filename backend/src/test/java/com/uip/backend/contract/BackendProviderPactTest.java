package com.uip.backend.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
 * <p>Verifies that the backend service fulfills contracts defined by its consumers
 * (frontend, mobile). Loads Pact files from the {@code pacts} classpath folder
 * (populated by {@code copyPactFiles} Gradle task from {@code build/pacts/}) and
 * verifies each interaction against the running Spring Boot test server.</p>
 *
 * <p><b>Disabled until CI Pact-broker flow is wired.</b> The
 * {@code PactVerificationInvocationContextProvider} extension resolves Pact files at
 * <em>extension registration</em> time and throws {@code NoPactsFoundException} before
 * any JUnit {@code @EnabledIf} / {@code @EnabledIfEnvironmentVariable} condition can
 * gate it, so the only way to keep {@code ./gradlew test} green without
 * {@code provider: backend} pact files is {@code @Disabled}. The generated pact files
 * in {@code build/pacts/} cover backend as a <em>consumer</em> of analytics-service /
 * notification-service, not backend as a provider.</p>
 *
 * <p><b>To enable:</b> wire frontend/mobile consumer tests to publish
 * {@code provider: backend} contracts to a Pact broker (or into {@code build/pacts/}),
 * then remove {@code @Disabled} here. The {@code copyPactFiles} Gradle task already
 * mirrors files into the test classpath.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Provider("backend")
@PactFolder("pacts")
@Tag("contract")
@Tag("integration")
@Disabled("Backend-as-provider Pact verification needs provider=backend pact files from "
        + "frontend/mobile consumer suites via Pact broker in CI. The @TestTemplate Pact "
        + "extension throws NoPactsFoundException at registration time before JUnit "
        + "condition guards can run, so @Disabled is required until the CI flow is wired. "
        + "Remove @Disabled once the broker publishes provider=backend contracts.")
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
