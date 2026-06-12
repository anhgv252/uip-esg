package com.uip.backend.traffic.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import com.uip.backend.traffic.api.dto.CongestionGeoJsonDto;
import com.uip.backend.traffic.api.dto.TrafficCountDto;
import com.uip.backend.traffic.api.dto.TrafficIncidentDto;
import com.uip.backend.traffic.service.TrafficService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GAP-021: @WebMvcTest slice tests for {@link TrafficController}.
 *
 * Covers: auth, GET /incidents, POST /incidents, validation, congestion-map.
 */
@WebMvcTest(
    controllers = TrafficController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.uip.backend.tenant.filter.TenantContextFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
    }
)
@Import(TrafficControllerWebMvcTest.MethodSecurityConfig.class)
@DisplayName("TrafficController — WebMvc")
class TrafficControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {}

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  TrafficService trafficService;
    @MockBean @SuppressWarnings("unused") TenantRateLimiter tenantRateLimiter;

    @BeforeEach
    void resetMocks() {
        reset(trafficService);
    }

    // ─── GET /incidents ───────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /incidents — authenticated returns paginated incidents")
    void getIncidents_authenticated_returnsPage() throws Exception {
        TrafficIncidentDto incident = TrafficIncidentDto.builder()
                .id(UUID.randomUUID())
                .intersectionId("INT-001")
                .incidentType("ACCIDENT")
                .description("Multi-vehicle collision")
                .latitude(10.7769)
                .longitude(106.7009)
                .status("OPEN")
                .occurredAt(LocalDateTime.of(2026, 6, 12, 8, 30))
                .build();

        Page<TrafficIncidentDto> page = new PageImpl<>(List.of(incident));
        when(trafficService.getIncidents(eq("OPEN"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/traffic/incidents")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].intersectionId").value("INT-001"))
                .andExpect(jsonPath("$.content[0].incidentType").value("ACCIDENT"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));

        verify(trafficService).getIncidents(eq("OPEN"), any(Pageable.class));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /incidents — default status filter is OPEN")
    void getIncidents_defaultStatusFilter() throws Exception {
        Page<TrafficIncidentDto> emptyPage = new PageImpl<>(List.of());
        when(trafficService.getIncidents(anyString(), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/traffic/incidents"))
                .andExpect(status().isOk());

        verify(trafficService).getIncidents(eq("OPEN"), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /incidents — unauthenticated rejects 401/403")
    void getIncidents_unauthenticated_rejects() throws Exception {
        mockMvc.perform(get("/api/v1/traffic/incidents"))
                .andExpect(status().is(greaterThanOrEqualTo(401)));
    }

    // ─── POST /incidents ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /incidents — ADMIN creates incident returns 201")
    void createIncident_admin_returns201() throws Exception {
        TrafficIncidentDto request = TrafficIncidentDto.builder()
                .intersectionId("INT-001")
                .incidentType("ACCIDENT")
                .description("Test accident")
                .latitude(10.7769)
                .longitude(106.7009)
                .status("OPEN")
                .build();

        TrafficIncidentDto response = TrafficIncidentDto.builder()
                .id(UUID.randomUUID())
                .intersectionId("INT-001")
                .incidentType("ACCIDENT")
                .description("Test accident")
                .latitude(10.7769)
                .longitude(106.7009)
                .status("OPEN")
                .occurredAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        when(trafficService.createIncident(any(TrafficIncidentDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/traffic/incidents")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intersectionId").value("INT-001"))
                .andExpect(jsonPath("$.incidentType").value("ACCIDENT"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        verify(trafficService).createIncident(any(TrafficIncidentDto.class));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    @DisplayName("POST /incidents — OPERATOR creates incident returns 201")
    void createIncident_operator_returns201() throws Exception {
        TrafficIncidentDto request = TrafficIncidentDto.builder()
                .intersectionId("INT-002")
                .incidentType("CONGESTION")
                .status("OPEN")
                .build();

        TrafficIncidentDto response = TrafficIncidentDto.builder()
                .id(UUID.randomUUID())
                .intersectionId("INT-002")
                .incidentType("CONGESTION")
                .status("OPEN")
                .build();

        when(trafficService.createIncident(any(TrafficIncidentDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/traffic/incidents")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(trafficService).createIncident(any(TrafficIncidentDto.class));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    @DisplayName("POST /incidents — CITIZEN forbidden 403")
    void createIncident_citizen_forbidden() throws Exception {
        TrafficIncidentDto request = TrafficIncidentDto.builder()
                .intersectionId("INT-001")
                .incidentType("ACCIDENT")
                .build();

        mockMvc.perform(post("/api/v1/traffic/incidents")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /incidents — unauthenticated rejects 401/403")
    void createIncident_unauthenticated_rejects() throws Exception {
        TrafficIncidentDto request = TrafficIncidentDto.builder()
                .intersectionId("INT-001")
                .incidentType("ACCIDENT")
                .build();

        mockMvc.perform(post("/api/v1/traffic/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(greaterThanOrEqualTo(401)));
    }

    // ─── GET /congestion-map ──────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /congestion-map — authenticated returns GeoJSON")
    void getCongestionMap_authenticated_returnsGeoJson() throws Exception {
        CongestionGeoJsonDto dto = CongestionGeoJsonDto.builder()
                .type("FeatureCollection")
                .features(List.of())
                .build();
        when(trafficService.getCongestionMap()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/traffic/congestion-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"));

        verify(trafficService).getCongestionMap();
    }

    // ─── GET /counts ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("CT-TRAFFIC-A1: GET /counts — authenticated returns 200 with count data")
    void getCounts_authenticated_returns200() throws Exception {
        TrafficCountDto count = TrafficCountDto.builder()
                .id(UUID.randomUUID())
                .intersectionId("INT-001")
                .recordedAt(LocalDateTime.of(2026, 6, 12, 8, 0))
                .vehicleCount(120)
                .vehicleType("CAR")
                .build();

        when(trafficService.getTrafficCounts(anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(count));

        mockMvc.perform(get("/api/v1/traffic/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].intersectionId").value("INT-001"))
                .andExpect(jsonPath("$[0].vehicleCount").value(120))
                .andExpect(jsonPath("$[0].vehicleType").value("CAR"));
    }

    @Test
    @WithMockUser
    @DisplayName("CT-TRAFFIC-A2: GET /counts — intersection param forwarded to service")
    void getCounts_withIntersection_forwardsParam() throws Exception {
        when(trafficService.getTrafficCounts(anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/traffic/counts")
                        .param("intersection", "INT-005"))
                .andExpect(status().isOk());

        verify(trafficService).getTrafficCounts(eq("INT-005"), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    // ─── PUT /incidents/{id}/status ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("CT-TRAFFIC-A3: PUT /incidents/{id}/status — ADMIN returns 200")
    void updateIncidentStatus_admin_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        TrafficIncidentDto response = TrafficIncidentDto.builder()
                .id(id)
                .intersectionId("INT-001")
                .incidentType("ACCIDENT")
                .status("RESOLVED")
                .build();

        when(trafficService.updateIncidentStatus(eq(id), eq("RESOLVED"))).thenReturn(response);

        mockMvc.perform(put("/api/v1/traffic/incidents/{id}/status", id)
                        .with(csrf())
                        .param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        verify(trafficService).updateIncidentStatus(eq(id), eq("RESOLVED"));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    @DisplayName("CT-TRAFFIC-A4: PUT /incidents/{id}/status — CITIZEN forbidden 403")
    void updateIncidentStatus_citizen_forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/traffic/incidents/{id}/status", UUID.randomUUID())
                        .with(csrf())
                        .param("status", "RESOLVED"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /congestion-map with features ─────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("CT-TRAFFIC-A5: GET /congestion-map — GeoJSON has features array")
    void getCongestionMap_hasFeaturesArray() throws Exception {
        CongestionGeoJsonDto.GeoJsonProperties props = new CongestionGeoJsonDto.GeoJsonProperties();
        props.setIntersectionId("INT-001");
        props.setCongestionLevel("HIGH");
        props.setAvgSpeed(15.5);

        CongestionGeoJsonDto.GeoJsonFeature feature = new CongestionGeoJsonDto.GeoJsonFeature();
        feature.setType("Feature");
        feature.setProperties(props);

        CongestionGeoJsonDto dto = CongestionGeoJsonDto.builder()
                .features(List.of(feature))
                .build();
        when(trafficService.getCongestionMap()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/traffic/congestion-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].properties.intersectionId").value("INT-001"))
                .andExpect(jsonPath("$.features[0].properties.congestionLevel").value("HIGH"));
    }
}
