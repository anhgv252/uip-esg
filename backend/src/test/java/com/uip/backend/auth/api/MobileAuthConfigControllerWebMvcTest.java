package com.uip.backend.auth.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import com.uip.backend.tenant.filter.TenantContextFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest slice for MobileAuthConfigController.
 * Tests the mobile PKCE config endpoint without Spring Security filters.
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
@DisplayName("MobileAuthConfigController — WebMvc")
class MobileAuthConfigControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    @SuppressWarnings("unused")
    private TenantRateLimiter tenantRateLimiter;

    @Nested
    @DisplayName("Config Endpoint")
    class ConfigEndpoint {

        @Test
        @DisplayName("PKCE-01: config endpoint returns issuer URL")
        void config_returnsIssuer() throws Exception {
            mockMvc.perform(get("/api/v1/mobile/auth/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.issuer").isNotEmpty())
                    .andExpect(jsonPath("$.issuer").isString());
        }

        @Test
        @DisplayName("PKCE-02: config returns required PKCE fields")
        void config_returnsJwtClaims() throws Exception {
            mockMvc.perform(get("/api/v1/mobile/auth/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clientId").isNotEmpty())
                    .andExpect(jsonPath("$.scopes").isNotEmpty())
                    .andExpect(jsonPath("$.redirectUri").isNotEmpty());
        }

        @Test
        @DisplayName("PKCE-03: endpoint accessible on multiple calls (stateless)")
        void config_refreshable() throws Exception {
            mockMvc.perform(get("/api/v1/mobile/auth/config"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/mobile/auth/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.issuer").isNotEmpty());
        }

        @Test
        @DisplayName("PKCE-04: public endpoint — no Authorization header needed")
        void config_publicEndpoint_noAuthRequired() throws Exception {
            mockMvc.perform(get("/api/v1/mobile/auth/config"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PKCE-05: clientId matches uip-mobile")
        void config_noJwtNeeded() throws Exception {
            mockMvc.perform(get("/api/v1/mobile/auth/config")
                        .param("tenantId", "hcm"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clientId").value("uip-mobile"));
        }
    }
}
