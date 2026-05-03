package com.uip.backend.auth.service;

import com.uip.backend.auth.api.dto.AuthResponse;
import com.uip.backend.auth.api.dto.LoginRequest;
import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.exception.InvalidCredentialsException;
import com.uip.backend.auth.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UipUserDetailsService userDetailsService;
    private final JwtTokenProvider tokenProvider;
    private final AppUserRepository appUserRepository;

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for username={}", request.username());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());

        // Load AppUser entity to resolve tenant claims and scopes
        Optional<AppUser> appUserOpt = appUserRepository.findByUsername(request.username());
        String tenantId = appUserOpt.map(AppUser::getTenantId).orElse("default");
        List<String> scopes = resolveScopes(appUserOpt);
        List<String> allowedBuildings = List.of();

        String accessToken = tokenProvider.generateAccessToken(userDetails, tenantId, scopes, allowedBuildings);
        String refreshToken = tokenProvider.generateRefreshToken(userDetails);
        long expiresIn = tokenProvider.getJwtProperties().getExpirationMs() / 1000;

        log.info("Successful login for username={}, tenant={}", request.username(), tenantId);
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }

    public AuthResponse refresh(String refreshToken) {
        String username = tokenProvider.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        if (!tokenProvider.isRefreshTokenValid(refreshToken, userDetails)) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        // Load AppUser entity to resolve tenant claims for refreshed token
        Optional<AppUser> appUserOpt = appUserRepository.findByUsername(username);
        String tenantId = appUserOpt.map(AppUser::getTenantId).orElse("default");
        List<String> scopes = resolveScopes(appUserOpt);
        List<String> allowedBuildings = List.of();

        String newAccessToken = tokenProvider.generateAccessToken(userDetails, tenantId, scopes, allowedBuildings);
        long expiresIn = tokenProvider.getJwtProperties().getExpirationMs() / 1000;
        log.info("Token refreshed for username={}, tenant={}", username, tenantId);
        return new AuthResponse(newAccessToken, refreshToken, "Bearer", expiresIn);
    }

    /**
     * Resolve permission scopes based on user role.
     * Falls back to empty scopes if AppUser is not found.
     */
    private List<String> resolveScopes(Optional<AppUser> appUserOpt) {
        return appUserOpt
                .map(appUser -> {
                    UserRole role = appUser.getRole();
                    if (role == UserRole.ROLE_ADMIN) {
                        return List.of(
                                "environment:read", "environment:write",
                                "esg:read", "esg:write",
                                "alert:read", "alert:ack",
                                "traffic:read", "traffic:write",
                                "sensor:read", "sensor:write",
                                "citizen:read", "citizen:admin",
                                "workflow:read", "workflow:write",
                                "tenant:admin"
                        );
                    } else if (role == UserRole.ROLE_OPERATOR) {
                        return List.of(
                                "environment:read", "environment:write",
                                "esg:read",
                                "alert:read", "alert:ack",
                                "traffic:read", "traffic:write",
                                "sensor:read", "sensor:write",
                                "workflow:read"
                        );
                    } else {
                        // ROLE_CITIZEN — read-only
                        return List.of(
                                "environment:read",
                                "esg:read",
                                "alert:read",
                                "traffic:read"
                        );
                    }
                })
                .orElse(List.of());
    }
}
