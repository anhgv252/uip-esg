package com.uip.analytics.contract;

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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pact Provider Verification Test — Analytics Service.
 *
 * <p>Verifies that analytics-service fulfills the contract defined by the
 * backend consumer test ({@code AnalyticsServiceConsumerPactTest}).</p>
 *
 * <p>Contract verification flow:
 * <ol>
 *   <li>Consumer test generates Pact files in {@code backend/build/pacts/}</li>
 *   <li>Pact files are copied to {@code src/test/resources/pacts/}</li>
 *   <li>This test loads Pact files and verifies provider responses</li>
 * </ol>
 *
 * <p>Run manually: {@code scripts/pact-verify.sh}</p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // Locally disable the production SecurityConfig for contract verification
    // only. This class-level property overrides the shared test profile so that
    // other tests (AnalyticsControllerTest) which rely on the real security
    // chain keep it enabled. Production is unaffected: matchIfMissing=true.
    properties = "uip.security.enabled=false"
)
@ActiveProfiles("test")
@Import({PactProviderStubConfiguration.class, com.uip.analytics.config.TestSecurityConfig.class})
@Provider("analytics-service")
@PactFolder("pacts")
@Tag("contract")
@Tag("integration")
@DisplayName("Pact Provider Verification — Analytics Service")
class AnalyticsServiceProviderPactTest {

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

    // ── Provider States ──────────────────────────────────────────────────────

    @State("analytics data exists for tenant alpha")
    void setUpAnalyticsDataForTenantAlpha() {
        // In test profile, repositories return seeded data.
        // No additional setup needed — ClickHouse testcontainers has test data.
    }
}
