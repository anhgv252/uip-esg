package com.uip.backend.citizen.service;

import com.uip.backend.citizen.api.dto.*;
import com.uip.backend.citizen.domain.Building;
import com.uip.backend.citizen.domain.CitizenAccount;
import com.uip.backend.citizen.domain.Household;
import com.uip.backend.citizen.repository.BuildingRepository;
import com.uip.backend.citizen.repository.CitizenAccountRepository;
import com.uip.backend.citizen.repository.HouseholdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CitizenService {
    
    private final CitizenAccountRepository citizenRepository;
    private final HouseholdRepository householdRepository;
    private final BuildingRepository buildingRepository;
    
    /**
     * Register a new citizen account
     * POST /api/v1/citizen/register (public endpoint)
     */
    @Transactional
    public CitizenProfileDto register(CitizenRegistrationRequest request) {
        log.info("Registering new citizen: {}", request.getEmail());
        
        // Validate email uniqueness
        if (citizenRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }
        
        // Generate username from email (safe approach)
        String username = generateUsername(request.getEmail());
        
        // Validate username uniqueness
        if (citizenRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        
        // Create citizen account
        CitizenAccount citizen = CitizenAccount.builder()
            .username(username)
            .email(request.getEmail())
            .phone(request.getPhone())
            .fullName(request.getFullName())
            .cccd(request.getCccd())
            .role("ROLE_CITIZEN")
            .build();
        
        CitizenAccount saved = citizenRepository.save(citizen);
        log.info("Citizen registered successfully: {} ({})", username, saved.getId());
        
        return mapToProfileDto(saved, null);
    }
    
    /**
     * Link a household to a citizen account
     * POST /api/v1/citizen/profile/household (authenticated)
     */
    @Transactional
    public CitizenProfileDto linkHousehold(String username, HouseholdRequest request) {
        log.info("Linking household for citizen: {}", username);
        
        CitizenAccount citizen = citizenRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Citizen not found: " + username));
        
        Building building = buildingRepository.findById(request.getBuildingId())
            .orElseThrow(() -> new IllegalArgumentException("Building not found: " + request.getBuildingId()));
        
        // Check if household already exists for this citizen
        householdRepository.findByCitizenId(citizen.getId()).ifPresent(existing -> {
            throw new IllegalStateException("Household already linked for this citizen");
        });
        
        // Create household
        Household household = Household.builder()
            .citizenId(citizen.getId())
            .buildingId(building.getId())
            .floor(request.getFloor())
            .unitNumber(request.getUnitNumber())
            .build();
        
        Household savedHousehold = householdRepository.save(household);
        log.info("Household linked: {} -> {}", citizen.getId(), savedHousehold.getId());
        
        return mapToProfileDto(citizen, savedHousehold);
    }
    
    /**
     * Get citizen profile with household info
     * GET /api/v1/citizen/profile (authenticated)
     */
    @Transactional(readOnly = true)
    public CitizenProfileDto getProfile(String username) {
        log.info("Fetching profile for citizen: {}", username);
        
        CitizenAccount citizen = citizenRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Citizen not found: " + username));
        
        Household household = householdRepository.findByCitizenId(citizen.getId()).orElse(null);
        
        return mapToProfileDto(citizen, household);
    }
    
    /**
     * Get all buildings for household setup
     * GET /api/v1/citizen/buildings (public)
     */
    @Transactional(readOnly = true)
    public List<BuildingDto> getBuildings() {
        log.info("Fetching building list");
        return buildingRepository.findAll().stream()
            .map(this::mapToBuildingDto)
            .toList();
    }
    
    /**
     * Get buildings by district
     */
    @Transactional(readOnly = true)
    public List<BuildingDto> getBuildingsByDistrict(String district) {
        log.info("Fetching buildings for district: {}", district);
        return buildingRepository.findByDistrict(district).stream()
            .map(this::mapToBuildingDto)
            .toList();
    }
    
    // Helper methods
    
    /**
     * Generate username from email (format: email prefix + random 4 digits)
     */
    private String generateUsername(String email) {
        String prefix = email.split("@")[0];
        String username = prefix.replaceAll("[^a-zA-Z0-9]", "");
        
        // Ensure uniqueness by appending random suffix if needed
        if (citizenRepository.existsByUsername(username)) {
            SecureRandom random = new SecureRandom();
            username = username + random.nextInt(10000);
        }
        
        return username;
    }
    
    private CitizenProfileDto mapToProfileDto(CitizenAccount citizen, Household household) {
        CitizenProfileDto dto = CitizenProfileDto.builder()
            .id(citizen.getId())
            .username(citizen.getUsername())
            .email(citizen.getEmail())
            .phone(citizen.getPhone())
            .fullName(citizen.getFullName())
            .cccd(citizen.getCccd())
            .role(citizen.getRole())
            .createdAt(citizen.getCreatedAt())
            .build();
        
        if (household != null) {
            Building building = buildingRepository.findById(household.getBuildingId()).orElse(null);
            dto.setHousehold(CitizenProfileDto.HouseholdDto.builder()
                .id(household.getId())
                .buildingId(household.getBuildingId())
                .buildingName(building != null ? building.getName() : null)
                .floor(household.getFloor())
                .unitNumber(household.getUnitNumber())
                .build());
        }
        
        return dto;
    }
    
    private BuildingDto mapToBuildingDto(Building building) {
        return BuildingDto.builder()
            .id(building.getId())
            .name(building.getName())
            .address(building.getAddress())
            .district(building.getDistrict())
            .build();
    }
}
