package com.uip.backend.traffic;

import com.uip.backend.traffic.api.dto.CongestionGeoJsonDto;
import com.uip.backend.traffic.api.dto.TrafficCountDto;
import com.uip.backend.traffic.api.dto.TrafficIncidentDto;
import com.uip.backend.traffic.domain.TrafficIncident;
import com.uip.backend.traffic.repository.TrafficCountRepository;
import com.uip.backend.traffic.repository.TrafficIncidentRepository;
import com.uip.backend.traffic.service.TrafficService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrafficService unit tests")
class TrafficServiceTest {

    @Mock TrafficIncidentRepository incidentRepository;
    @Mock TrafficCountRepository countRepository;

    @InjectMocks TrafficService trafficService;

    private TrafficIncident openIncident;
    private UUID incidentId;

    @BeforeEach
    void setUp() {
        incidentId = UUID.randomUUID();
        openIncident = TrafficIncident.builder()
                .id(incidentId)
                .intersectionId("INT-001")
                .incidentType("ACCIDENT")
                .status("OPEN")
                .latitude(10.776)
                .longitude(106.700)
                .occurredAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    @Test
    @DisplayName("getTrafficCounts: should return empty list when no counts in range")
    void getTrafficCounts_emptyRange() {
        when(countRepository.findByIntersectionAndTimeRange(eq("INT-001"), any(), any()))
                .thenReturn(List.of());

        List<TrafficCountDto> result = trafficService.getTrafficCounts(
                "INT-001", LocalDateTime.now().minusHours(1), LocalDateTime.now());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getIncidents: should return paged incidents for given status")
    void getIncidents_returnsPage() {
        var page = new PageImpl<>(List.of(openIncident));
        when(incidentRepository.findByStatus(eq("OPEN"), any(Pageable.class))).thenReturn(page);

        var result = trafficService.getIncidents("OPEN", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getIncidentType()).isEqualTo("ACCIDENT");
    }

    @Test
    @DisplayName("getIncidents: should return empty page for no matching status")
    void getIncidents_noneFound() {
        when(incidentRepository.findByStatus(eq("RESOLVED"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = trafficService.getIncidents("RESOLVED", PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("createIncident: should persist and return DTO")
    void createIncident_success() {
        TrafficIncidentDto dto = new TrafficIncidentDto();
        dto.setIntersectionId("INT-002");
        dto.setIncidentType("CONGESTION");
        dto.setDescription("Heavy traffic after accident");
        dto.setLatitude(10.780);
        dto.setLongitude(106.705);

        when(incidentRepository.save(any(TrafficIncident.class))).thenReturn(openIncident);

        TrafficIncidentDto result = trafficService.createIncident(dto);

        assertThat(result).isNotNull();
        verify(incidentRepository).save(any(TrafficIncident.class));
    }

    @Test
    @DisplayName("updateIncidentStatus: should update status to RESOLVED and set resolvedAt")
    void updateIncidentStatus_toResolved() {
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(openIncident));
        when(incidentRepository.save(any(TrafficIncident.class))).thenAnswer(i -> i.getArgument(0));

        TrafficIncidentDto result = trafficService.updateIncidentStatus(incidentId, "RESOLVED");

        assertThat(result.getStatus()).isEqualTo("RESOLVED");
        assertThat(openIncident.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateIncidentStatus: should throw when incident not found")
    void updateIncidentStatus_notFound() {
        UUID unknown = UUID.randomUUID();
        when(incidentRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trafficService.updateIncidentStatus(unknown, "RESOLVED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Incident not found");
    }

    @Test
    @DisplayName("getCongestionMap: should return GeoJSON FeatureCollection")
    void getCongestionMap_returnsGeoJson() {
        when(incidentRepository.findRecentByStatus(eq("OPEN"), any()))
                .thenReturn(List.of(openIncident));

        CongestionGeoJsonDto result = trafficService.getCongestionMap();

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("FeatureCollection");
    }
}

