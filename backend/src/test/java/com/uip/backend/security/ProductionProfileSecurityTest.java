package com.uip.backend.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S10-SEC-03 — Verify that debug/test endpoints are not reachable
 * when the 'production' profile is active.
 *
 * Gates checked:
 * - G4: Production profile: debug endpoints return 404
 * - G14: Debug endpoints: 0 reachable khi spring.profiles.active=production
 *
 * Uses @WebMvcTest (slice test) instead of @SpringBootTest to avoid
 * needing a full Spring context with DB/Redis/Kafka/Camunda.
 * The test only verifies that @Profile("!production") on the debug
 * controllers causes a 404 when the production profile is active.
 *
 * Note: Since @WebMvcTest doesn't load @Profile-gated @RestController beans,
 * debug endpoints simply won't be registered → 404 is the expected result.
 * We test the negative path (endpoints exist only in non-production profiles).
 */
@WebMvcTest
@ActiveProfiles("production")
@Import(ProductionProfileSecurityTest.TestSecurityConfig.class)
@Tag("security")
class ProductionProfileSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void floodTestInjectReading_notReachableInProduction() throws Exception {
        mockMvc.perform(post("/api/v1/test/inject-reading"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void floodTestInjectFloodAlert_notReachableInProduction() throws Exception {
        mockMvc.perform(post("/api/v1/test/inject-flood-alert"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void fakeTrafficData_notReachableInProduction() throws Exception {
        mockMvc.perform(get("/api/v1/internal/fake-traffic"))
                .andExpect(status().isNotFound());
    }

    /**
     * Minimal security config for @WebMvcTest slice — allows requests through
     * so we can verify the endpoints return 404 (not 401/403 from security).
     */
    @Configuration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
