package com.uip.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("production")
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
    void fakeTrafficData_notReachableInProduction() throws Exception {
        mockMvc.perform(get("/api/v1/internal/fake-traffic"))
                .andExpect(status().isNotFound());
    }
}
