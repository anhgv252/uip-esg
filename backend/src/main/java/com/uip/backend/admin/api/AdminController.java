package com.uip.backend.admin.api;

import com.uip.backend.admin.api.dto.SensorRegistryDto;
import com.uip.backend.admin.api.dto.UserSummaryDto;
import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.environment.api.dto.SensorDto;
import com.uip.backend.environment.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin — Management", description = "User management and sensor registry (ADMIN only)")
public class AdminController {

    private final AppUserRepository userRepository;
    private final EnvironmentService environmentService;

    // ─── User Management ────────────────────────────────────────────────────

    @GetMapping("/users")
    @Operation(summary = "List all users with pagination")
    public ResponseEntity<Page<UserSummaryDto>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.ASC, "username"));
        return ResponseEntity.ok(
                userRepository.findAll(pageable).map(this::toUserDto));
    }

    @PutMapping("/users/{username}/role")
    @Transactional
    @Operation(summary = "Change a user's role")
    public ResponseEntity<UserSummaryDto> changeRole(
            @PathVariable String username,
            @RequestParam String role) {

        UserRole newRole;
        try {
            newRole = UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));

        user.setRole(newRole);
        return ResponseEntity.ok(toUserDto(userRepository.save(user)));
    }

    @PutMapping("/users/{username}/deactivate")
    @Transactional
    @Operation(summary = "Deactivate a user account")
    public ResponseEntity<UserSummaryDto> deactivateUser(@PathVariable String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
        user.setActive(false);
        return ResponseEntity.ok(toUserDto(userRepository.save(user)));
    }

    // ─── Sensor Registry ────────────────────────────────────────────────────

    @GetMapping("/sensors")
    @Operation(summary = "List all sensors (active and inactive)")
    public ResponseEntity<List<SensorRegistryDto>> listSensors() {
        return ResponseEntity.ok(
                environmentService.listAllSensors().stream()
                        .map(this::toSensorDto)
                        .toList());
    }

    @PutMapping("/sensors/{id}/status")
    @Operation(summary = "Toggle sensor active/inactive status")
    public ResponseEntity<SensorRegistryDto> toggleSensorStatus(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(toSensorDto(environmentService.toggleSensor(id, active)));
    }

    // ─── Mappers ────────────────────────────────────────────────────────────

    private UserSummaryDto toUserDto(AppUser u) {
        return UserSummaryDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .role(u.getRole().name())
                .active(u.isActive())
                .createdAt(u.getCreatedAt())
                .build();
    }

    private SensorRegistryDto toSensorDto(SensorDto s) {
        return SensorRegistryDto.builder()
                .id(s.getId())
                .sensorId(s.getSensorId())
                .sensorName(s.getSensorName())
                .sensorType(s.getSensorType())
                .districtCode(s.getDistrictCode())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .active(s.isActive())
                .lastSeenAt(s.getLastSeenAt())
                .installedAt(s.getInstalledAt())
                .build();
    }
}
