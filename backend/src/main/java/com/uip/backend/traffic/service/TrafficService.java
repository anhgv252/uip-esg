package com.uip.backend.traffic.service;

import com.uip.backend.traffic.api.dto.CongestionGeoJsonDto;
import com.uip.backend.traffic.api.dto.TrafficCountDto;
import com.uip.backend.traffic.api.dto.TrafficIncidentDto;
import com.uip.backend.traffic.domain.TrafficCount;
import com.uip.backend.traffic.domain.TrafficIncident;
import com.uip.backend.traffic.repository.TrafficCountRepository;
import com.uip.backend.traffic.repository.TrafficIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrafficService {
    
    private final TrafficIncidentRepository incidentRepository;
    private final TrafficCountRepository countRepository;
    
    /**
     * Get vehicle counts for an intersection within a time range
     */
    @Transactional(readOnly = true)
    public List<TrafficCountDto> getTrafficCounts(String intersectionId, LocalDateTime from, LocalDateTime to) {
        log.info("Fetching traffic counts for intersection {} from {} to {}", intersectionId, from, to);
        List<TrafficCount> counts = countRepository.findByIntersectionAndTimeRange(intersectionId, from, to);
        return counts.stream()
            .map(this::mapToCountDto)
            .toList();
    }
    
    /**
     * Get incidents with optional filtering
     */
    @Transactional(readOnly = true)
    public Page<TrafficIncidentDto> getIncidents(String status, Pageable pageable) {
        log.info("Fetching incidents with status: {}", status);
        Page<TrafficIncident> incidents = incidentRepository.findByStatus(status, pageable);
        return incidents.map(this::mapToIncidentDto);
    }
    
    /**
     * Get all open incidents
     */
    @Transactional(readOnly = true)
    public List<TrafficIncidentDto> getOpenIncidents() {
        log.info("Fetching all open incidents");
        List<TrafficIncident> incidents = incidentRepository.findRecentByStatus("OPEN", Pageable.unpaged());
        return incidents.stream()
            .map(this::mapToIncidentDto)
            .toList();
    }
    
    /**
     * Create a new incident
     */
    @Transactional
    public TrafficIncidentDto createIncident(TrafficIncidentDto dto) {
        log.info("Creating new incident for intersection {}", dto.getIntersectionId());
        TrafficIncident incident = TrafficIncident.builder()
            .intersectionId(dto.getIntersectionId())
            .incidentType(dto.getIncidentType())
            .description(dto.getDescription())
            .latitude(dto.getLatitude())
            .longitude(dto.getLongitude())
            .status("OPEN")
            .occurredAt(dto.getOccurredAt() != null ? dto.getOccurredAt() : LocalDateTime.now())
            .build();
        
        TrafficIncident saved = incidentRepository.save(incident);
        return mapToIncidentDto(saved);
    }
    
    /**
     * Update incident status
     */
    @Transactional
    public TrafficIncidentDto updateIncidentStatus(UUID incidentId, String newStatus) {
        log.info("Updating incident {} status to {}", incidentId, newStatus);
        TrafficIncident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
        
        incident.setStatus(newStatus);
        if ("RESOLVED".equals(newStatus)) {
            incident.setResolvedAt(LocalDateTime.now());
        }
        
        TrafficIncident updated = incidentRepository.save(incident);
        return mapToIncidentDto(updated);
    }
    
    /**
     * Get congestion layer as GeoJSON for map visualization
     * Aggregates recent traffic data by intersection
     */
    @Transactional(readOnly = true)
    public CongestionGeoJsonDto getCongestionMap() {
        log.info("Generating congestion GeoJSON map");
        
        // Get all open incidents
        List<TrafficIncident> incidents = incidentRepository.findRecentByStatus("OPEN", Pageable.unpaged());
        
        // Get recent counts from last hour
        LocalDateTime oneHourAgo = LocalDateTime.now().minus(1, ChronoUnit.HOURS);
        List<TrafficCountDto> recentCounts = new ArrayList<>();
        
        Set<String> intersectionIds = new HashSet<>();
        for (TrafficIncident incident : incidents) {
            if (incident.getIntersectionId() != null) {
                intersectionIds.add(incident.getIntersectionId());
            }
        }
        
        // Add sample intersections if no incidents
        if (intersectionIds.isEmpty()) {
            intersectionIds.addAll(List.of("INT-001", "INT-002", "INT-003", "INT-004", "INT-005"));
        }
        
        for (String intId : intersectionIds) {
            List<TrafficCount> counts = countRepository.findByIntersectionAndTimeRange(intId, oneHourAgo, LocalDateTime.now());
            recentCounts.addAll(counts.stream().map(this::mapToCountDto).toList());
        }
        
        // Build GeoJSON features
        List<CongestionGeoJsonDto.GeoJsonFeature> features = new ArrayList<>();
        
        // Map of intersection -> aggregated data
        Map<String, List<TrafficCountDto>> byIntersection = new HashMap<>();
        recentCounts.forEach(count -> 
            byIntersection.computeIfAbsent(count.getIntersectionId(), k -> new ArrayList<>()).add(count)
        );
        
        // Create features per intersection
        byIntersection.forEach((intId, counts) -> {
            int avgCount = (int) counts.stream()
                .mapToInt(TrafficCountDto::getVehicleCount)
                .average()
                .orElse(0);
            
            String congestionLevel = determineCongestionLevel(avgCount);
            
            // Find incident for this intersection
            Optional<TrafficIncident> incident = incidents.stream()
                .filter(i -> i.getIntersectionId().equals(intId))
                .findFirst();
            
            double lat = incident.map(TrafficIncident::getLatitude).orElse(10.7769);
            double lng = incident.map(TrafficIncident::getLongitude).orElse(106.7009);
            
            CongestionGeoJsonDto.GeoJsonGeometry geometry = new CongestionGeoJsonDto.GeoJsonGeometry();
            geometry.setCoordinates(new double[]{lng, lat});
            
            CongestionGeoJsonDto.GeoJsonProperties properties = new CongestionGeoJsonDto.GeoJsonProperties();
            properties.setIntersectionId(intId);
            properties.setVehicleCount(avgCount);
            properties.setCongestionLevel(congestionLevel);
            properties.setDescription(incident.map(TrafficIncident::getDescription).orElse("Normal traffic"));
            properties.setAvgSpeed(calculateAvgSpeed(avgCount));
            
            CongestionGeoJsonDto.GeoJsonFeature feature = new CongestionGeoJsonDto.GeoJsonFeature();
            feature.setGeometry(geometry);
            feature.setProperties(properties);
            features.add(feature);
        });
        
        return CongestionGeoJsonDto.builder()
            .features(features)
            .build();
    }
    
    /**
     * Helper: determine congestion level based on vehicle count
     */
    private String determineCongestionLevel(int vehicleCount) {
        if (vehicleCount < 100) return "LOW";
        if (vehicleCount < 300) return "MODERATE";
        if (vehicleCount < 500) return "HIGH";
        return "SEVERE";
    }
    
    /**
     * Helper: calculate average speed based on congestion
     */
    private Double calculateAvgSpeed(int vehicleCount) {
        if (vehicleCount < 100) return 50.0; // km/h
        if (vehicleCount < 300) return 35.0;
        if (vehicleCount < 500) return 20.0;
        return 10.0;
    }
    
    // DTOs mapping helpers
    private TrafficIncidentDto mapToIncidentDto(TrafficIncident incident) {
        return TrafficIncidentDto.builder()
            .id(incident.getId())
            .intersectionId(incident.getIntersectionId())
            .incidentType(incident.getIncidentType())
            .description(incident.getDescription())
            .latitude(incident.getLatitude())
            .longitude(incident.getLongitude())
            .status(incident.getStatus())
            .occurredAt(incident.getOccurredAt())
            .resolvedAt(incident.getResolvedAt())
            .createdAt(incident.getCreatedAt())
            .build();
    }
    
    private TrafficCountDto mapToCountDto(TrafficCount count) {
        return TrafficCountDto.builder()
            .id(count.getId())
            .intersectionId(count.getIntersectionId())
            .recordedAt(count.getRecordedAt())
            .vehicleCount(count.getVehicleCount())
            .vehicleType(count.getVehicleType())
            .build();
    }
}
