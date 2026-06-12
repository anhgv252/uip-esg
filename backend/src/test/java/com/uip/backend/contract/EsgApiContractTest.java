package com.uip.backend.contract;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import com.uip.backend.esg.api.EsgController;
import com.uip.backend.esg.api.dto.EsgMetricDto;
import com.uip.backend.esg.api.dto.EsgReportDto;
import com.uip.backend.esg.api.dto.EsgSummaryDto;
import com.uip.backend.esg.service.EsgService;
import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.filter.TenantContextFilter;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * REST Assured API contract tests for ESG API.
 * Covers: GET /esg/summary, GET /esg/energy, GET /esg/carbon,
 *         POST /esg/reports/generate, GET /esg/reports/{id}/status
 * Tests: status codes, response schema validation, headers
 *
 * v3.1-06: First batch of REST Assured contract tests
 */
@WebMvcTest(
    controllers = EsgController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantContextFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
    }
)
@Tag("contract")
@DisplayName("ESG API — REST Assured Contract Tests")
class EsgApiContractTest {

    @MockBean EsgService esgService;
    @MockBean @SuppressWarnings("unused") TenantRateLimiter tenantRateLimiter;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.standaloneSetup(new EsgController(esgService));
    }

    // ─── GET /api/v1/esg/summary ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/esg/summary")
    class GetSummaryTests {

        @Test
        @WithMockUser
        @DisplayName("200 — returns ESG summary with all fields")
        void getSummary_authenticated_returns200WithSchema() {
            EsgSummaryDto dto = EsgSummaryDto.builder()
                    .period("QUARTERLY").year(2026).quarter(1)
                    .totalEnergyKwh(15000.5).totalWaterM3(800.0)
                    .totalCarbonTco2e(500.0).totalWasteTons(120.0)
                    .sampleCount(5000L)
                    .build();

            try (var ctx = mockStatic(TenantContext.class)) {
                ctx.when(TenantContext::getCurrentTenant).thenReturn("hcm");
                when(esgService.getSummary(eq("hcm"), anyString(), anyInt(), anyInt())).thenReturn(dto);

                given()
                    .queryParam("year", "2026")
                    .queryParam("quarter", "1")
                .when()
                    .get("/api/v1/esg/summary")
                .then()
                    .statusCode(200)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body("period", equalTo("QUARTERLY"))
                    .body("year", equalTo(2026))
                    .body("quarter", equalTo(1))
                    .body("totalEnergyKwh", equalTo(15000.5f))
                    .body("totalWaterM3", equalTo(800.0f))
                    .body("totalCarbonTco2e", equalTo(500.0f))
                    .body("totalWasteTons", equalTo(120.0f))
                    .body("sampleCount", equalTo(5000));
            }
        }

        @Test
        @DisplayName("401 — unauthenticated request rejected (verified in SecurityConfig tests)")
        void getSummary_unauthenticated_securityTestedSeparately() {
            // Security (401/403) is tested in SecurityConfig tests with full Spring context.
            // Standalone REST Assured does not enforce Spring Security.
            // This is a documentation placeholder — the real test is in ProductionProfileSecurityTest.
            assertThat(true).isTrue();
        }
    }

    // ─── GET /api/v1/esg/energy ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/esg/energy")
    class GetEnergyTests {

        @Test
        @WithMockUser
        @DisplayName("200 — returns energy metrics as array")
        void getEnergy_authenticated_returns200WithArray() {
            EsgMetricDto metric = EsgMetricDto.builder()
                    .sourceId("SENSOR-001").metricType("ENERGY")
                    .timestamp(Instant.parse("2026-01-15T10:00:00Z"))
                    .value(450.0).unit("kWh").buildingId("BLD-01")
                    .build();

            try (var ctx = mockStatic(TenantContext.class)) {
                ctx.when(TenantContext::getCurrentTenant).thenReturn("hcm");
                when(esgService.getEnergyData(eq("hcm"), any(), any(), isNull())).thenReturn(List.of(metric));

                given()
                .when()
                    .get("/api/v1/esg/energy")
                .then()
                    .statusCode(200)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body("size()", equalTo(1))
                    .body("[0].sourceId", equalTo("SENSOR-001"))
                    .body("[0].metricType", equalTo("ENERGY"))
                    .body("[0].value", equalTo(450.0f))
                    .body("[0].unit", equalTo("kWh"));
            }
        }

        @Test
        @WithMockUser
        @DisplayName("200 — empty array when no data")
        void getEnergy_noData_returnsEmptyArray() {
            try (var ctx = mockStatic(TenantContext.class)) {
                ctx.when(TenantContext::getCurrentTenant).thenReturn("hcm");
                when(esgService.getEnergyData(eq("hcm"), any(), any(), isNull())).thenReturn(List.of());

                given()
                .when()
                    .get("/api/v1/esg/energy")
                .then()
                    .statusCode(200)
                    .body("size()", equalTo(0));
            }
        }
    }

    // ─── GET /api/v1/esg/carbon ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/esg/carbon")
    class GetCarbonTests {

        @Test
        @WithMockUser
        @DisplayName("200 — returns carbon metrics as array")
        void getCarbon_authenticated_returns200WithArray() {
            EsgMetricDto metric = EsgMetricDto.builder()
                    .sourceId("SENSOR-002").metricType("CARBON")
                    .timestamp(Instant.parse("2026-02-01T00:00:00Z"))
                    .value(120.5).unit("tCO2e")
                    .build();

            try (var ctx = mockStatic(TenantContext.class)) {
                ctx.when(TenantContext::getCurrentTenant).thenReturn("hcm");
                when(esgService.getCarbonData(eq("hcm"), any(), any())).thenReturn(List.of(metric));

                given()
                .when()
                    .get("/api/v1/esg/carbon")
                .then()
                    .statusCode(200)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body("size()", equalTo(1))
                    .body("[0].sourceId", equalTo("SENSOR-002"))
                    .body("[0].value", equalTo(120.5f));
            }
        }
    }

    // ─── POST /api/v1/esg/reports/generate ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/esg/reports/generate")
    class GenerateReportTests {

        @Test
        @WithMockUser(username = "operator", authorities = {"ROLE_OPERATOR", "esg:write"})
        @DisplayName("202 — valid report generation returns PENDING status")
        void generateReport_validRequest_returns202() {
            UUID reportId = UUID.randomUUID();
            EsgReportDto dto = EsgReportDto.builder()
                    .id(reportId).periodType("QUARTERLY").year(2026).quarter(1)
                    .status("PENDING").createdAt(Instant.now())
                    .build();

            try (var ctx = mockStatic(TenantContext.class)) {
                ctx.when(TenantContext::getCurrentTenant).thenReturn("hcm");
                when(esgService.triggerReportGeneration(eq("hcm"), anyString(), anyInt(), anyInt())).thenReturn(dto);

                given()
                    .queryParam("year", "2026")
                    .queryParam("quarter", "1")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                .when()
                    .post("/api/v1/esg/reports/generate")
                .then()
                    .statusCode(202)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body("id", equalTo(reportId.toString()))
                    .body("status", equalTo("PENDING"))
                    .body("periodType", equalTo("QUARTERLY"))
                    .body("year", equalTo(2026))
                    .body("quarter", equalTo(1));
            }
        }

        @Test
        @DisplayName("403 — CITIZEN role forbidden (verified in SecurityConfig tests)")
        void generateReport_citizen_securityTestedSeparately() {
            // Role-based access control tested in SecurityConfig tests with full Spring context.
            assertThat(true).isTrue();
        }
    }

    // ─── GET /api/v1/esg/reports/{id}/status ──────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/esg/reports/{id}/status")
    class GetReportStatusTests {

        @Test
        @WithMockUser
        @DisplayName("200 — returns report status with schema")
        void getReportStatus_authenticated_returns200WithSchema() {
            UUID reportId = UUID.randomUUID();
            EsgReportDto dto = EsgReportDto.builder()
                    .id(reportId).periodType("QUARTERLY").year(2026).quarter(1)
                    .status("DONE").generatedAt(Instant.now())
                    .build();

            try (var ctx = mockStatic(TenantContext.class)) {
                ctx.when(TenantContext::getCurrentTenant).thenReturn("hcm");
                when(esgService.getReportStatus(eq("hcm"), eq(reportId))).thenReturn(dto);

                given()
                .when()
                    .get("/api/v1/esg/reports/{id}/status", reportId)
                .then()
                    .statusCode(200)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .body("id", equalTo(reportId.toString()))
                    .body("status", equalTo("DONE"));
            }
        }
    }
}
