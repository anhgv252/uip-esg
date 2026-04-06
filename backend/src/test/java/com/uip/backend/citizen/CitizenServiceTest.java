package com.uip.backend.citizen;

import com.uip.backend.citizen.api.dto.*;
import com.uip.backend.citizen.domain.Building;
import com.uip.backend.citizen.domain.CitizenAccount;
import com.uip.backend.citizen.domain.Household;
import com.uip.backend.citizen.repository.BuildingRepository;
import com.uip.backend.citizen.repository.CitizenAccountRepository;
import com.uip.backend.citizen.repository.HouseholdRepository;
import com.uip.backend.citizen.service.CitizenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CitizenService unit tests")
class CitizenServiceTest {

    @Mock CitizenAccountRepository citizenRepository;
    @Mock HouseholdRepository householdRepository;
    @Mock BuildingRepository buildingRepository;

    @InjectMocks CitizenService citizenService;

    private CitizenRegistrationRequest validRequest;
    private CitizenAccount savedCitizen;
    private Building building;

    @BeforeEach
    void setUp() {
        validRequest = CitizenRegistrationRequest.builder()
                .fullName("Nguyen Van A")
                .email("nguyenvana@example.com")
                .phone("0912345678")
                .cccd("079123456789")
                .password("password123")
                .build();

        savedCitizen = new CitizenAccount();
        savedCitizen.setId(UUID.randomUUID());
        savedCitizen.setUsername("nguyenvana");
        savedCitizen.setEmail("nguyenvana@example.com");
        savedCitizen.setFullName("Nguyen Van A");
        savedCitizen.setRole("ROLE_CITIZEN");

        building = new Building();
        building.setId(UUID.randomUUID());
        building.setName("Vinhomes Central Park");
        building.setAddress("720A Điện Biên Phủ");
        building.setDistrict("Bình Thạnh");
    }

    @Test
    @DisplayName("register: should create citizen with generated username")
    void register_success() {
        when(citizenRepository.existsByEmail(validRequest.getEmail())).thenReturn(false);
        when(citizenRepository.existsByUsername(any())).thenReturn(false);
        when(citizenRepository.save(any(CitizenAccount.class))).thenReturn(savedCitizen);

        CitizenProfileDto result = citizenService.register(validRequest);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("nguyenvana@example.com");
        verify(citizenRepository).save(any(CitizenAccount.class));
    }

    @Test
    @DisplayName("register: should reject duplicate email")
    void register_duplicateEmail() {
        when(citizenRepository.existsByEmail(validRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> citizenService.register(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");

        verify(citizenRepository, never()).save(any());
    }

    @Test
    @DisplayName("getBuildings: should return all buildings from repository")
    void getBuildings_returnsList() {
        when(buildingRepository.findAll()).thenReturn(List.of(building));

        List<BuildingDto> result = citizenService.getBuildings();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Vinhomes Central Park");
    }

    @Test
    @DisplayName("getBuildings: should return empty list when no buildings")
    void getBuildings_empty() {
        when(buildingRepository.findAll()).thenReturn(List.of());

        List<BuildingDto> result = citizenService.getBuildings();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProfile: should throw when citizen not found")
    void getProfile_notFound() {
        when(citizenRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> citizenService.getProfile("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Citizen not found");
    }

    @Test
    @DisplayName("linkHousehold: should throw when building not found")
    void linkHousehold_buildingNotFound() {
        UUID buildingId = UUID.randomUUID();
        HouseholdRequest request = new HouseholdRequest();
        request.setBuildingId(buildingId);
        request.setFloor("3");
        request.setUnitNumber("301");

        when(citizenRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(savedCitizen));
        when(buildingRepository.findById(buildingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> citizenService.linkHousehold("nguyenvana", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Building not found");
    }

    @Test
    @DisplayName("linkHousehold: should persist household successfully")
    void linkHousehold_success() {
        UUID buildingId = building.getId();
        HouseholdRequest request = new HouseholdRequest();
        request.setBuildingId(buildingId);
        request.setFloor("5");
        request.setUnitNumber("502");

        Household household = new Household();
        household.setId(UUID.randomUUID());
        household.setCitizenId(savedCitizen.getId());
        household.setBuildingId(buildingId);
        household.setFloor("5");
        household.setUnitNumber("502");

        when(citizenRepository.findByUsername("nguyenvana")).thenReturn(Optional.of(savedCitizen));
        when(buildingRepository.findById(buildingId)).thenReturn(Optional.of(building));
        when(householdRepository.findByCitizenId(savedCitizen.getId())).thenReturn(Optional.empty());
        when(householdRepository.save(any(Household.class))).thenReturn(household);

        CitizenProfileDto result = citizenService.linkHousehold("nguyenvana", request);

        assertThat(result).isNotNull();
        verify(householdRepository).save(any(Household.class));
    }
}

