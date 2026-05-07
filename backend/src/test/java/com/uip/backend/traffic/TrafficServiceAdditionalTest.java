package com.uip.backend.traffic;

import com.uip.backend.traffic.api.dto.CongestionGeoJsonDto;
import com.uip.backend.traffic.api.dto.TrafficCountDto;
import com.uip.backend.traffic.domain.TrafficCount;
import com.uip.backend.traffic.domain.TrafficIncident;
import com.uip.backend.traffic.repository.TrafficCountRepository;
import com.uip.backend.traffic.repository.TrafficIncidentRepository;
import com.uip.backend.traffic.service.TrafficService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrafficServiceAdditionalTest {

    @Mock TrafficIncidentRepository incidentRepository;
    @Mock TrafficCountRepository countRepository;

    TrafficService trafficService;

    @BeforeEach
    void setUp() {
        trafficService = new TrafficService(incidentRepository, countRepository);
    }

    @Test
    void createIncident_withNullOccurredAt_usesNow() {
        TrafficIncident saved = TrafficIncident.builder()
                .id(UUID.randomUUID()).intersectionId("INT-X").incidentType("FLOOD")
                .status("OPEN").latitude(10.7).longitude(106.7)
                .occurredAt(LocalDateTime.now()).build();
        when(incidentRepository.save(any())).thenReturn(saved);

        var dto = new com.uip.backend.traffic.api.dto.TrafficIncidentDto();
        dto.setIntersectionId("INT-X");
        dto.setIncidentType("FLOOD");
        dto.setOccurredAt(null); // should default to now

        var result = trafficService.createIncident(dto);
        assertThat(result).isNotNull();
    }

    @Test
    void updateIncidentStatus_nonResolved_noResolvedAt() {
        UUID id = UUID.randomUUID();
        TrafficIncident incident = TrafficIncident.builder().id(id).intersectionId("INT-Y").status("OPEN").build();
        when(incidentRepository.findById(id)).thenReturn(java.util.Optional.of(incident));
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = trafficService.updateIncidentStatus(id, "IN_PROGRESS");
        assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(incident.getResolvedAt()).isNull();
    }

    @Test
    void getTrafficCounts_returnsMapped() {
        TrafficCount count = TrafficCount.builder()
                .id(UUID.randomUUID()).intersectionId("INT-01")
                .vehicleCount(50).vehicleType("CAR").recordedAt(LocalDateTime.now()).build();
        when(countRepository.findByIntersectionAndTimeRange(eq("INT-01"), any(), any()))
                .thenReturn(List.of(count));

        List<TrafficCountDto> result = trafficService.getTrafficCounts(
                "INT-01", LocalDateTime.now().minusHours(1), LocalDateTime.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVehicleCount()).isEqualTo(50);
    }

    @Test
    void getCongestionMap_noIncidents_usesSampleIntersections() {
        when(incidentRepository.findRecentByStatus(eq("OPEN"), any(Pageable.class)))
                .thenReturn(List.of());
        when(countRepository.findByIntersectionAndTimeRange(any(), any(), any()))
                .thenReturn(List.of());

        CongestionGeoJsonDto result = trafficService.getCongestionMap();

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("FeatureCollection");
        // No counts → no features
        assertThat(result.getFeatures()).isEmpty();
    }

    @Test
    void getCongestionMap_withCounts_buildsFeaturesForEachIntersection() {
        TrafficIncident incident = TrafficIncident.builder()
                .id(UUID.randomUUID()).intersectionId("INT-A")
                .status("OPEN").latitude(10.8).longitude(106.8).build();
        when(incidentRepository.findRecentByStatus(eq("OPEN"), any(Pageable.class)))
                .thenReturn(List.of(incident));

        TrafficCount count = TrafficCount.builder()
                .id(UUID.randomUUID()).intersectionId("INT-A")
                .vehicleCount(200).recordedAt(LocalDateTime.now()).build();
        when(countRepository.findByIntersectionAndTimeRange(eq("INT-A"), any(), any()))
                .thenReturn(List.of(count));

        CongestionGeoJsonDto result = trafficService.getCongestionMap();

        assertThat(result.getFeatures()).hasSize(1);
        var feature = result.getFeatures().get(0);
        assertThat(feature.getProperties().getIntersectionId()).isEqualTo("INT-A");
        assertThat(feature.getProperties().getCongestionLevel()).isEqualTo("MODERATE");
        assertThat(feature.getProperties().getAvgSpeed()).isEqualTo(35.0);
    }

    @Test
    void getCongestionMap_severeCongestion() {
        TrafficIncident incident = TrafficIncident.builder()
                .id(UUID.randomUUID()).intersectionId("INT-B")
                .status("OPEN").latitude(10.9).longitude(106.9).build();
        when(incidentRepository.findRecentByStatus(eq("OPEN"), any(Pageable.class)))
                .thenReturn(List.of(incident));

        TrafficCount count = TrafficCount.builder()
                .id(UUID.randomUUID()).intersectionId("INT-B")
                .vehicleCount(600).recordedAt(LocalDateTime.now()).build();
        when(countRepository.findByIntersectionAndTimeRange(eq("INT-B"), any(), any()))
                .thenReturn(List.of(count));

        CongestionGeoJsonDto result = trafficService.getCongestionMap();

        var props = result.getFeatures().get(0).getProperties();
        assertThat(props.getCongestionLevel()).isEqualTo("SEVERE");
        assertThat(props.getAvgSpeed()).isEqualTo(10.0);
    }

    @Test
    void getCongestionMap_highCongestion() {
        TrafficIncident incident = TrafficIncident.builder()
                .id(UUID.randomUUID()).intersectionId("INT-C")
                .status("OPEN").latitude(10.5).longitude(106.5).build();
        when(incidentRepository.findRecentByStatus(eq("OPEN"), any(Pageable.class)))
                .thenReturn(List.of(incident));

        TrafficCount count = TrafficCount.builder()
                .id(UUID.randomUUID()).intersectionId("INT-C")
                .vehicleCount(400).recordedAt(LocalDateTime.now()).build();
        when(countRepository.findByIntersectionAndTimeRange(eq("INT-C"), any(), any()))
                .thenReturn(List.of(count));

        CongestionGeoJsonDto result = trafficService.getCongestionMap();

        var props = result.getFeatures().get(0).getProperties();
        assertThat(props.getCongestionLevel()).isEqualTo("HIGH");
        assertThat(props.getAvgSpeed()).isEqualTo(20.0);
    }

    @Test
    void getCongestionMap_lowCongestion() {
        TrafficIncident incident = TrafficIncident.builder()
                .id(UUID.randomUUID()).intersectionId("INT-D")
                .status("OPEN").latitude(10.6).longitude(106.6).build();
        when(incidentRepository.findRecentByStatus(eq("OPEN"), any(Pageable.class)))
                .thenReturn(List.of(incident));

        TrafficCount count = TrafficCount.builder()
                .id(UUID.randomUUID()).intersectionId("INT-D")
                .vehicleCount(50).recordedAt(LocalDateTime.now()).build();
        when(countRepository.findByIntersectionAndTimeRange(eq("INT-D"), any(), any()))
                .thenReturn(List.of(count));

        CongestionGeoJsonDto result = trafficService.getCongestionMap();

        var props = result.getFeatures().get(0).getProperties();
        assertThat(props.getCongestionLevel()).isEqualTo("LOW");
        assertThat(props.getAvgSpeed()).isEqualTo(50.0);
    }

    @Test
    void getOpenIncidents_returnsMapped() {
        TrafficIncident incident = TrafficIncident.builder()
                .id(UUID.randomUUID()).intersectionId("INT-E")
                .incidentType("ACCIDENT").status("OPEN")
                .occurredAt(LocalDateTime.now().minusHours(2)).build();
        when(incidentRepository.findRecentByStatus(eq("OPEN"), any(Pageable.class)))
                .thenReturn(List.of(incident));

        var result = trafficService.getOpenIncidents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIncidentType()).isEqualTo("ACCIDENT");
    }
}
