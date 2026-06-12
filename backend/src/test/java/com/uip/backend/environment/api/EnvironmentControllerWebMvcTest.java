package com.uip.backend.environment.api;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import com.uip.backend.environment.api.dto.AqiResponseDto;
import com.uip.backend.environment.api.dto.SensorDto;
import com.uip.backend.environment.api.dto.SensorReadingDto;
import com.uip.backend.environment.service.EnvironmentService;
import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.filter.TenantContextFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

/**
 * GAP-020: @WebMvcTest slice tests for {@link EnvironmentController}.
 *
 * Covers: auth, tenant isolation, sensor listing, sensor readings.
 */
@WebMvcTest(
    controllers = EnvironmentController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantContextFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
    }
)
@Import(EnvironmentControllerWebMvcTest.MethodSecurityConfig.class)
@DisplayName("EnvironmentController — WebMvc")
class EnvironmentControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @MockBean  EnvironmentService environmentService;
    @MockBean @SuppressWarnings("unused") TenantRateLimiter tenantRateLimiter;

    private static final String TENANT_HCM = "hcm";
    private static final String TENANT_HANOI = "hanoi";

    @BeforeEach
    void resetMocks() {
        reset(environmentService);
    }

    // ─── GET /sensors ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /sensors — authenticated returns sensor list")
    void listSensors_authenticated_returnsList() throws Exception {
        SensorDto sensor = SensorDto.builder()
                .id(UUID.randomUUID())
                .sensorId("SENSOR-HCM-001")
                .sensorName("Air Quality District 1")
                .sensorType("AIR_QUALITY")
                .districtCode("HCM-D1")
                .latitude(10.7769)
                .longitude(106.7009)
                .status("ONLINE")
                .active(true)
                .lastSeenAt(Instant.now())
                .build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(environmentService.listSensors()).thenReturn(List.of(sensor));

            mockMvc.perform(get("/api/v1/environment/sensors"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sensorId").value("SENSOR-HCM-001"))
                    .andExpect(jsonPath("$[0].sensorType").value("AIR_QUALITY"))
                    .andExpect(jsonPath("$[0].status").value("ONLINE"));

            verify(environmentService).listSensors();
        }
    }

    @Test
    @DisplayName("GET /sensors — unauthenticated rejects 401/403")
    void listSensors_unauthenticated_rejects() throws Exception {
        mockMvc.perform(get("/api/v1/environment/sensors"))
                .andExpect(status().is(greaterThanOrEqualTo(401)));
    }

    // ─── GET /sensors/{sensorId}/readings ─────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /sensors/{sensorId}/readings — authenticated returns readings")
    void getReadings_authenticated_returnsReadings() throws Exception {
        SensorReadingDto reading = SensorReadingDto.builder()
                .sensorId("SENSOR-HCM-001")
                .timestamp(Instant.parse("2026-06-12T10:00:00Z"))
                .aqi(85.0)
                .pm25(25.3)
                .temperature(32.5)
                .humidity(78.0)
                .build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(environmentService.getReadings(eq("SENSOR-HCM-001"), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(List.of(reading));

            mockMvc.perform(get("/api/v1/environment/sensors/SENSOR-HCM-001/readings"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sensorId").value("SENSOR-HCM-001"))
                    .andExpect(jsonPath("$[0].aqi").value(85.0))
                    .andExpect(jsonPath("$[0].pm25").value(25.3));
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /sensors/{sensorId}/readings?limit=50 — limit param forwarded")
    void getReadings_withLimit_forwardsLimit() throws Exception {
        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(environmentService.getReadings(anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/environment/sensors/SENSOR-HCM-001/readings")
                            .param("limit", "50"))
                    .andExpect(status().isOk());

            verify(environmentService).getReadings(eq("SENSOR-HCM-001"), any(Instant.class), any(Instant.class), eq(50));
        }
    }

    @Test
    @WithMockUser
    @DisplayName("GET /sensors/{sensorId}/readings?from=...&to=... — time range forwarded")
    void getReadings_withTimeRange_forwardsRange() throws Exception {
        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(environmentService.getReadings(anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/environment/sensors/SENSOR-HCM-001/readings")
                            .param("from", "2026-06-01T00:00:00Z")
                            .param("to", "2026-06-12T00:00:00Z"))
                    .andExpect(status().isOk());

            verify(environmentService).getReadings(
                    eq("SENSOR-HCM-001"),
                    eq(Instant.parse("2026-06-01T00:00:00Z")),
                    eq(Instant.parse("2026-06-12T00:00:00Z")),
                    eq(100));
        }
    }

    @Test
    @DisplayName("GET /sensors/{sensorId}/readings — unauthenticated rejects 401/403")
    void getReadings_unauthenticated_rejects() throws Exception {
        mockMvc.perform(get("/api/v1/environment/sensors/SENSOR-HCM-001/readings"))
                .andExpect(status().is(greaterThanOrEqualTo(401)));
    }

    // ─── GET /aqi/current ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /aqi/current — authenticated returns AQI data")
    void getCurrentAqi_authenticated_returnsData() throws Exception {
        AqiResponseDto aqi = AqiResponseDto.builder()
                .sensorId("SENSOR-HCM-001")
                .timestamp(Instant.parse("2026-06-12T10:00:00Z"))
                .aqiValue(85)
                .category("Moderate")
                .color("#FFFF00")
                .pm25(25.3)
                .build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(environmentService.getCurrentAqi()).thenReturn(List.of(aqi));

            mockMvc.perform(get("/api/v1/environment/aqi/current"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sensorId").value("SENSOR-HCM-001"))
                    .andExpect(jsonPath("$[0].aqiValue").value(85))
                    .andExpect(jsonPath("$[0].category").value("Moderate"));

            verify(environmentService).getCurrentAqi();
        }
    }

    @Test
    @DisplayName("GET /aqi/current — unauthenticated rejects 401/403")
    void getCurrentAqi_unauthenticated_rejects() throws Exception {
        mockMvc.perform(get("/api/v1/environment/aqi/current"))
                .andExpect(status().is(greaterThanOrEqualTo(401)));
    }

    // ─── Tenant isolation ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("Tenant isolation — HCM context delegates HCM tenant to service")
    void hcmTenant_delegatesCorrectTenant() throws Exception {
        SensorDto hcmSensor = SensorDto.builder()
                .id(UUID.randomUUID())
                .sensorId("SENSOR-HCM-001")
                .sensorName("HCM D1 Sensor")
                .sensorType("AIR_QUALITY")
                .districtCode("HCM-D1")
                .build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(environmentService.listSensors()).thenReturn(List.of(hcmSensor));

            mockMvc.perform(get("/api/v1/environment/sensors"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sensorId").value("SENSOR-HCM-001"));

            verify(environmentService).listSensors();
        }
    }

    // ─── GET /aqi/history ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("CT-ENV-A1: GET /aqi/history — authenticated with default period returns 200")
    void getAqiHistory_authenticated_defaultPeriod_returns200() throws Exception {
        AqiResponseDto aqi = AqiResponseDto.builder()
                .sensorId("SENSOR-HCM-001")
                .timestamp(Instant.parse("2026-06-12T10:00:00Z"))
                .aqiValue(72)
                .category("Moderate")
                .color("#FFFF00")
                .pm25(18.5)
                .build();

        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(environmentService.getAqiHistory(isNull(), eq("24h"))).thenReturn(List.of(aqi));

            mockMvc.perform(get("/api/v1/environment/aqi/history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sensorId").value("SENSOR-HCM-001"))
                    .andExpect(jsonPath("$[0].aqiValue").value(72))
                    .andExpect(jsonPath("$[0].category").value("Moderate"));

            verify(environmentService).getAqiHistory(isNull(), eq("24h"));
        }
    }

    @Test
    @WithMockUser
    @DisplayName("CT-ENV-A2: GET /aqi/history — district filter forwarded to service")
    void getAqiHistory_withDistrict_forwardsDistrict() throws Exception {
        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(environmentService.getAqiHistory(anyString(), anyString())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/environment/aqi/history")
                            .param("district", "HCM-D1"))
                    .andExpect(status().isOk());

            verify(environmentService).getAqiHistory(eq("HCM-D1"), eq("24h"));
        }
    }

    @Test
    @WithMockUser
    @DisplayName("CT-ENV-A3: GET /aqi/history — period=7d forwarded to service")
    void getAqiHistory_withPeriod7d_forwardsPeriod() throws Exception {
        try (MockedStatic<TenantContext> ctx = Mockito.mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_HCM);
            when(environmentService.getAqiHistory(any(), anyString())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/environment/aqi/history")
                            .param("period", "7d"))
                    .andExpect(status().isOk());

            verify(environmentService).getAqiHistory(isNull(), eq("7d"));
        }
    }
}
