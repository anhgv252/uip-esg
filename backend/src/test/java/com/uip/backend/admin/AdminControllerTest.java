package com.uip.backend.admin;

import com.uip.backend.admin.api.AdminController;
import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.environment.api.dto.SensorDto;
import com.uip.backend.environment.service.EnvironmentService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController unit tests")
class AdminControllerTest {

    @Mock
    AppUserRepository userRepository;

    @Mock
    EnvironmentService environmentService;

    @InjectMocks
    AdminController adminController;

    // ─── User Management ────────────────────────────────────────────────────

    @Test
    @DisplayName("listUsers — returns paged user summaries")
    void listUsers_returnsPagedUsers() {
        AppUser user = buildUser("jdoe", "jdoe@test.com", UserRole.ROLE_CITIZEN);
        Page<AppUser> page = new PageImpl<>(List.of(user));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        ResponseEntity<?> response = adminController.listUsers(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("changeRole — valid role updates user and returns 200")
    void changeRole_validRole_returns200() {
        AppUser user = buildUser("jdoe", "jdoe@test.com", UserRole.ROLE_CITIZEN);
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        ResponseEntity<?> response = adminController.changeRole("jdoe", "ROLE_OPERATOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_OPERATOR);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changeRole — invalid role string returns 400 Bad Request (BUG-S3-07-01)")
    void changeRole_invalidRole_returns400() {
        ResponseEntity<?> response = adminController.changeRole("jdoe", "SUPERUSER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("changeRole — unknown user throws EntityNotFoundException")
    void changeRole_unknownUser_throwsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminController.changeRole("ghost", "ROLE_ADMIN"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("deactivateUser — sets active=false and returns 200")
    void deactivateUser_setsActiveToFalse() {
        AppUser user = buildUser("jdoe", "jdoe@test.com", UserRole.ROLE_CITIZEN);
        user.setActive(true);
        when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        ResponseEntity<?> response = adminController.deactivateUser("jdoe");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("deactivateUser — unknown user throws EntityNotFoundException")
    void deactivateUser_unknownUser_throwsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminController.deactivateUser("ghost"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    // ─── Sensor Registry ────────────────────────────────────────────────────

    @Test
    @DisplayName("listSensors — returns all sensors sorted by name")
    void listSensors_returnsAllSensors() {
        SensorDto dto = buildSensorDto("AIR-Q1-001");
        when(environmentService.listAllSensors()).thenReturn(List.of(dto));

        ResponseEntity<?> response = adminController.listSensors();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(environmentService).listAllSensors();
    }

    @Test
    @DisplayName("toggleSensorStatus — sets active=false and returns 200")
    void toggleSensorStatus_deactivatesSensor() {
        UUID id = UUID.randomUUID();
        SensorDto deactivatedDto = buildSensorDto("AIR-Q1-001");
        when(environmentService.toggleSensor(id, false)).thenReturn(deactivatedDto);

        ResponseEntity<?> response = adminController.toggleSensorStatus(id, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(environmentService).toggleSensor(id, false);
    }

    @Test
    @DisplayName("toggleSensorStatus — sets active=true reactivates sensor")
    void toggleSensorStatus_activatesSensor() {
        UUID id = UUID.randomUUID();
        SensorDto activatedDto = buildSensorDto("AIR-Q1-002");
        when(environmentService.toggleSensor(id, true)).thenReturn(activatedDto);

        ResponseEntity<?> response = adminController.toggleSensorStatus(id, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(environmentService).toggleSensor(id, true);
    }

    @Test
    @DisplayName("toggleSensorStatus — unknown sensor propagates EntityNotFoundException")
    void toggleSensorStatus_unknownSensor_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(environmentService.toggleSensor(id, false))
                .thenThrow(new EntityNotFoundException("Sensor not found: " + id));

        assertThatThrownBy(() -> adminController.toggleSensorStatus(id, false))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private AppUser buildUser(String username, String email, UserRole role) {
        return new AppUser(username, email, "hashed_password", role);
    }

    private SensorDto buildSensorDto(String sensorId) {
        return SensorDto.builder()
                .id(UUID.randomUUID())
                .sensorId(sensorId)
                .sensorName("Sensor " + sensorId)
                .sensorType("AIR_QUALITY")
                .districtCode("Q1")
                .latitude(10.762)
                .longitude(106.660)
                .status("ONLINE")
                .lastSeenAt(Instant.now())
                .build();
    }
}
