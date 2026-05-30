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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * S6-M03 — MobileAuthConfigController tests.
 * Public endpoint — no auth required.
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
class MobileAuthConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    @SuppressWarnings("unused")
    private TenantRateLimiter tenantRateLimiter;

    @Test
    @DisplayName("GET /config returns all required fields")
    void getConfig_returnsAllFields() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").exists())
                .andExpect(jsonPath("$.clientId").exists())
                .andExpect(jsonPath("$.scopes").exists())
                .andExpect(jsonPath("$.redirectUri").exists());
    }

    @Test
    @DisplayName("GET /config?tenantId=hcm returns issuer with tenant")
    void getConfig_withTenantParam_returnsIssuerWithTenant() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/auth/config")
                        .param("tenantId", "hcm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").exists())
                .andExpect(jsonPath("$.clientId").value("uip-mobile"));
    }

    @Test
    @DisplayName("GET /config default returns valid config")
    void getConfig_defaultTenant_isValid() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").isNotEmpty())
                .andExpect(jsonPath("$.scopes").isNotEmpty());
    }
}
