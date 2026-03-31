package com.uip.backend.auth.service;

import com.uip.backend.auth.api.dto.AuthResponse;
import com.uip.backend.auth.api.dto.LoginRequest;
import com.uip.backend.auth.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UipUserDetailsService userDetailsService;
    private final JwtTokenProvider tokenProvider;

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
        String accessToken = tokenProvider.generateAccessToken(userDetails);
        String refreshToken = tokenProvider.generateRefreshToken(userDetails);
        long expiresIn = tokenProvider.getJwtProperties().getExpirationMs() / 1000;

        log.info("Successful login for username={}", request.username());
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }

    public AuthResponse refresh(String refreshToken) {
        String username = tokenProvider.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        if (!tokenProvider.isRefreshTokenValid(refreshToken, userDetails)) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        String newAccessToken = tokenProvider.generateAccessToken(userDetails);
        long expiresIn = tokenProvider.getJwtProperties().getExpirationMs() / 1000;
        log.info("Token refreshed for username={}", username);
        return new AuthResponse(newAccessToken, refreshToken, "Bearer", expiresIn);
    }
}
