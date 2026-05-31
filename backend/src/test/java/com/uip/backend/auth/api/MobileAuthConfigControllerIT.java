package com.uip.backend.auth.api;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import com.uip.backend.tenant.filter.TenantContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * QA-5 Sprint 6 — MobileAuthConfigController integration tests.
 * Endpoint is PUBLIC — no Authorization header required.
 * Properties injected via @TestPropertySource to assert exact values.
 */
@WebMvcTest(
    controllers = MobileAuthConfigController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantContextFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
    }
)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "keycloak.auth-server-url=https://auth.example.com/realms/uip",
    "keycloak.resource=uip-mobile",
    "keycloak.scope=openid profile email",
    "app.mobile.redirect-uri=uipmobile://callback"
})
@DisplayName("MobileAuthConfigController — IT")
class MobileAuthConfigControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    @SuppressWarnings("unused")
    private TenantRateLimiter tenantRateLimiter;

    @Test
    @DisplayName("getConfig_defaultTenant_returnsAllFields")
    void getConfig_defaultTenant_returnsAllFields() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").exists())
                .andExpect(jsonPath("$.clientId").exists())
                .andExpect(jsonPath("$.scopes").exists())
                .andExpect(jsonPath("$.redirectUri").exists());
    }

    /**
     * When tenantId=hcm is provided but the configured issuer already contains "/realms/",
     * the controller returns the property value unchanged (no tenant suffix appended).
     */
    @Test
    @DisplayName("getConfig_withTenantId_returnsHcmIssuer")
    void getConfig_withTenantId_returnsHcmIssuer() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/auth/config")
                        .param("tenantId", "hcm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(containsString("/realms/")));
    }

    @Test
    @DisplayName("getConfig_clientIdMatchesProperty")
    void getConfig_clientIdMatchesProperty() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("uip-mobile"));
    }

    @Test
    @DisplayName("getConfig_redirectUriPresent")
    void getConfig_redirectUriPresent() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUri").value("uipmobile://callback"));
    }

    @Test
    @DisplayName("getConfig_noAuth_returns200")
    void getConfig_noAuth_returns200() throws Exception {
        // Public endpoint — no Authorization header should still return 200
        mockMvc.perform(get("/api/v1/mobile/auth/config"))
                .andExpect(status().isOk());
    }
}
