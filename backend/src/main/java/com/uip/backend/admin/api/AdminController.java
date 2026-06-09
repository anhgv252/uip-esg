package com.uip.backend.admin.api;

import com.uip.backend.admin.api.dto.CreateSensorRequest;
import com.uip.backend.admin.api.dto.SensorRegistryDto;
import com.uip.backend.admin.api.dto.UserSummaryDto;
import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.environment.api.dto.SensorDto;
import com.uip.backend.environment.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final AppUserRepository userRepository;
    private final EnvironmentService environmentService;

    // ─── User Management ────────────────────────────────────────────────────

    @GetMapping("/users")
    @Operation(summary = "List all users with pagination")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated user list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    })
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
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User role updated"),
        @ApiResponse(responseCode = "400", description = "Invalid role value"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
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
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User deactivated"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserSummaryDto> deactivateUser(@PathVariable String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
        user.setActive(false);
        return ResponseEntity.ok(toUserDto(userRepository.save(user)));
    }

    // ─── Sensor Registry ────────────────────────────────────────────────────

    @GetMapping("/sensors")
    @Operation(summary = "List all sensors (active and inactive)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sensor list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    })
    public ResponseEntity<List<SensorRegistryDto>> listSensors() {
        return ResponseEntity.ok(
                environmentService.listAllSensors().stream()
                        .map(this::toSensorDto)
                        .toList());
    }

    @PutMapping("/sensors/{id}/status")
    @Operation(summary = "Toggle sensor active/inactive status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sensor status updated"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Sensor not found")
    })
    public ResponseEntity<SensorRegistryDto> toggleSensorStatus(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(toSensorDto(environmentService.toggleSensor(id, active)));
    }

    @PostMapping("/sensors")
    @Operation(summary = "Create a new sensor")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Sensor created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    })
    public ResponseEntity<SensorRegistryDto> createSensor(
            @RequestBody @jakarta.validation.Valid CreateSensorRequest request) {
        return ResponseEntity.status(201).body(
                toSensorDto(environmentService.createSensor(
                        request.sensorId(), request.sensorName(), request.sensorType(),
                        request.districtCode(), request.latitude(), request.longitude())));
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
