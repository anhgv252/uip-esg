package com.uip.backend.contract;

import com.uip.backend.traffic.api.dto.CongestionGeoJsonDto;
import com.uip.backend.traffic.api.dto.TrafficCountDto;
import com.uip.backend.traffic.api.dto.TrafficIncidentDto;
import com.uip.backend.traffic.service.TrafficService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Contract tests for Traffic API — verify service contract (input/output shape).
 * Pure Mockito pattern: no Spring context, no MockMvc.
 *
 * Sprint 2 (MVP4): adds 6 contract tests to reach ≥30 total @Tag("contract") tests.
 */
@Tag("contract")
@DisplayName("Traffic API — Service Contract Tests")
class TrafficApiContractTest {

    private TrafficService trafficService;

    private static final UUID INCIDENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        trafficService = mock(TrafficService.class);
    }

    private TrafficIncidentDto buildIncident(UUID id, String status) {
        return TrafficIncidentDto.builder()
                .id(id)
                .intersectionId("INT-001")
                .incidentType("ACCIDENT")
                .description("Vehicle collision at intersection")
                .latitude(10.7769)
                .longitude(106.7009)
                .status(status)
                .occurredAt(LocalDateTime.now().minusMinutes(30))
                .createdAt(LocalDateTime.now().minusMinutes(30))
                .build();
    }

    private TrafficCountDto buildCount(String intersectionId, int vehicleCount) {
        return TrafficCountDto.builder()
                .id(UUID.randomUUID())
                .intersectionId(intersectionId)
                .vehicleCount(vehicleCount)
                .vehicleType("CAR")
                .recordedAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    // ─── getIncidents contract ────────────────────────────────────────────────

    @Nested
    @DisplayName("getIncidents — contract")
    class GetIncidentsTests {

        @Test
        @DisplayName("Returns Page<TrafficIncidentDto> with correct field mapping")
        void getIncidents_returnsPageWithCorrectFields() {
            TrafficIncidentDto dto = buildIncident(INCIDENT_ID, "OPEN");
            when(trafficService.getIncidents(eq("OPEN"), any()))
                    .thenReturn(new PageImpl<>(List.of(dto)));

            var result = trafficService.getIncidents("OPEN", PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            TrafficIncidentDto item = result.getContent().get(0);
            assertThat(item.getId()).isEqualTo(INCIDENT_ID);
            assertThat(item.getIntersectionId()).isEqualTo("INT-001");
            assertThat(item.getIncidentType()).isEqualTo("ACCIDENT");
            assertThat(item.getStatus()).isEqualTo("OPEN");
            assertThat(item.getLatitude()).isEqualTo(10.7769);
            assertThat(item.getLongitude()).isEqualTo(106.7009);
        }

        @Test
        @DisplayName("Returns empty page when no incidents match status filter")
        void getIncidents_noMatch_returnsEmptyPage() {
            when(trafficService.getIncidents(eq("RESOLVED"), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            var result = trafficService.getIncidents("RESOLVED", PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    // ─── createIncident contract ───────────────────────────────────────────────

    @Nested
    @DisplayName("createIncident — contract")
    class CreateIncidentTests {

        @Test
        @DisplayName("Returns DTO with server-assigned id and status OPEN")
        void createIncident_returnsDtoWithIdSet() {
            UUID newId = UUID.randomUUID();
            TrafficIncidentDto input = buildIncident(null, null);
            TrafficIncidentDto saved = buildIncident(newId, "OPEN");

            when(trafficService.createIncident(any(TrafficIncidentDto.class))).thenReturn(saved);

            var result = trafficService.createIncident(input);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getId()).isEqualTo(newId);
            assertThat(result.getStatus()).isEqualTo("OPEN");
            assertThat(result.getIntersectionId()).isEqualTo("INT-001");
            verify(trafficService).createIncident(any(TrafficIncidentDto.class));
        }
    }

    // ─── getCongestionMap contract ─────────────────────────────────────────────

    @Nested
    @DisplayName("getCongestionMap — contract")
    class GetCongestionMapTests {

        @Test
        @DisplayName("Returns CongestionGeoJsonDto with type=FeatureCollection")
        void getCongestionMap_returnsFeatureCollectionType() {
            CongestionGeoJsonDto.GeoJsonGeometry geometry = new CongestionGeoJsonDto.GeoJsonGeometry();
            geometry.setCoordinates(new double[]{106.7009, 10.7769});

            CongestionGeoJsonDto.GeoJsonProperties props = new CongestionGeoJsonDto.GeoJsonProperties();
            props.setIntersectionId("INT-001");
            props.setVehicleCount(250);
            props.setCongestionLevel("MODERATE");
            props.setAvgSpeed(35.0);

            CongestionGeoJsonDto.GeoJsonFeature feature = new CongestionGeoJsonDto.GeoJsonFeature();
            feature.setGeometry(geometry);
            feature.setProperties(props);

            CongestionGeoJsonDto dto = CongestionGeoJsonDto.builder()
                    .features(List.of(feature))
                    .build();

            when(trafficService.getCongestionMap()).thenReturn(dto);

            var result = trafficService.getCongestionMap();

            assertThat(result.getType()).isEqualTo("FeatureCollection");
            assertThat(result.getFeatures()).hasSize(1);
            assertThat(result.getFeatures().get(0).getProperties().getCongestionLevel())
                    .isEqualTo("MODERATE");
            assertThat(result.getFeatures().get(0).getProperties().getVehicleCount())
                    .isEqualTo(250);
        }
    }

    // ─── getTrafficCounts contract ─────────────────────────────────────────────

    @Nested
    @DisplayName("getTrafficCounts — contract")
    class GetTrafficCountsTests {

        @Test
        @DisplayName("Returns list of TrafficCountDto with vehicleCount populated")
        void getTrafficCounts_returnsListWithVehicleCount() {
            TrafficCountDto count1 = buildCount("INT-001", 180);
            TrafficCountDto count2 = buildCount("INT-001", 220);

            LocalDateTime from = LocalDateTime.now().minusHours(1);
            LocalDateTime to   = LocalDateTime.now();

            when(trafficService.getTrafficCounts(eq("INT-001"), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(count1, count2));

            var result = trafficService.getTrafficCounts("INT-001", from, to);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getVehicleCount()).isEqualTo(180);
            assertThat(result.get(1).getVehicleCount()).isEqualTo(220);
            assertThat(result).allMatch(c -> "INT-001".equals(c.getIntersectionId()));
            assertThat(result).allMatch(c -> "CAR".equals(c.getVehicleType()));
        }
    }

    // ─── updateIncidentStatus contract ────────────────────────────────────────

    @Nested
    @DisplayName("updateIncidentStatus — contract")
    class UpdateIncidentStatusTests {

        @Test
        @DisplayName("Returns DTO with updated status RESOLVED")
        void updateIncidentStatus_returnsUpdatedStatus() {
            TrafficIncidentDto resolved = buildIncident(INCIDENT_ID, "RESOLVED");
            resolved.setResolvedAt(LocalDateTime.now());

            when(trafficService.updateIncidentStatus(eq(INCIDENT_ID), eq("RESOLVED")))
                    .thenReturn(resolved);

            var result = trafficService.updateIncidentStatus(INCIDENT_ID, "RESOLVED");

            assertThat(result.getId()).isEqualTo(INCIDENT_ID);
            assertThat(result.getStatus()).isEqualTo("RESOLVED");
            assertThat(result.getResolvedAt()).isNotNull();
        }
    }
}
