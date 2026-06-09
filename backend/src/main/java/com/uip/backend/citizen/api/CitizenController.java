package com.uip.backend.citizen.api;

import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.auth.service.JwtTokenProvider;
import com.uip.backend.auth.service.UipUserDetailsService;
import com.uip.backend.citizen.api.dto.*;
import com.uip.backend.citizen.service.CitizenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * S3-04 — Citizen Account Module
 * Public registration and authenticated profile management
 */
@RestController
@RequestMapping("/api/v1/citizen")
@Tag(name = "Citizen", description = "Citizen account registration, profile, and household management")
@RequiredArgsConstructor
public class CitizenController {

    private final CitizenService citizenService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UipUserDetailsService userDetailsService;

    @Value("${server.ssl.enabled:false}")
    private boolean secureCookie;

    /**
     * Register a new citizen account (public endpoint, no auth required).
     * Creates both a CitizenAccount (citizen module) and an AppUser (auth module)
     * in one transaction so the client receives a JWT immediately — avoiding the
     * Step-2 "401 no session" problem in the registration wizard.
     *
     * POST /api/v1/citizen/register
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new citizen account and receive a JWT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Account created with JWT"),
            @ApiResponse(responseCode = "400", description = "Invalid registration data")
    })
    @Transactional
    public ResponseEntity<CitizenRegistrationResponse> register(
            @Valid @RequestBody CitizenRegistrationRequest request,
            HttpServletResponse response) {

        // 1. Create CitizenAccount (citizen module)
        CitizenProfileDto profile = citizenService.register(request);

        // 2. Create AppUser with ROLE_CITIZEN so JWT auth works for Step 2
        String username = profile.getUsername();
        if (!appUserRepository.existsByUsername(username)) {
            AppUser appUser = new AppUser(
                    username,
                    request.getEmail(),
                    passwordEncoder.encode(request.getPassword()),
                    UserRole.ROLE_CITIZEN
            );
            appUserRepository.save(appUser);
        }

        // 3. Generate JWT
        var userDetails = userDetailsService.loadUserByUsername(username);
        String accessToken  = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);
        long   expiresIn    = jwtTokenProvider.getJwtProperties().getExpirationMs() / 1000;

        // 4. Set httpOnly cookie (mirrors AuthController)
        Cookie cookie = new Cookie("access_token", accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge((int) expiresIn);
        response.addCookie(cookie);

        return ResponseEntity.status(201).body(
                new CitizenRegistrationResponse(profile, accessToken, refreshToken, "Bearer", expiresIn));
    }

    /**
     * Get all buildings for household setup (public).
     * GET /api/v1/citizen/buildings
     */
    @GetMapping("/buildings")
    @Operation(summary = "Get all buildings for household registration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Building list")
    })
    public ResponseEntity<List<BuildingDto>> getBuildings() {
        List<BuildingDto> buildings = citizenService.getBuildings();
        return ResponseEntity.ok(buildings);
    }

    /**
     * GET /api/v1/citizen/buildings/by-district?district=Quận1 (public)
     */
    @GetMapping("/buildings/by-district")
    @Operation(summary = "Get buildings by district")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Buildings in district"),
            @ApiResponse(responseCode = "400", description = "Missing district parameter")
    })
    public ResponseEntity<List<BuildingDto>> getBuildingsByDistrict(
            @RequestParam String district) {
        List<BuildingDto> buildings = citizenService.getBuildingsByDistrict(district);
        return ResponseEntity.ok(buildings);
    }

    /**
     * Get citizen profile with household info (authenticated).
     * GET /api/v1/citizen/profile
     */
    @GetMapping("/profile")
    @Operation(summary = "Get current citizen profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Citizen profile"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT"),
            @ApiResponse(responseCode = "404", description = "Citizen profile not found")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CitizenProfileDto> getProfile(Authentication authentication) {
        String username = authentication.getName();
        CitizenProfileDto profile = citizenService.getProfile(username);
        return ResponseEntity.ok(profile);
    }

    /**
     * Link household to citizen account (authenticated).
     * POST /api/v1/citizen/profile/household
     */
    @PostMapping("/profile/household")
    @Operation(summary = "Link household to citizen account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Household linked"),
            @ApiResponse(responseCode = "400", description = "Invalid household data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT"),
            @ApiResponse(responseCode = "403", description = "Forbidden — requires CITIZEN or ADMIN role")
    })
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN')")
    public ResponseEntity<CitizenProfileDto> linkHousehold(
            @Valid @RequestBody HouseholdRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        CitizenProfileDto result = citizenService.linkHousehold(username, request);
        return ResponseEntity.ok(result);
    }

}

