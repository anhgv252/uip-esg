package com.uip.backend.tenant.service;

import com.uip.backend.auth.api.dto.AuthResponse;
import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.auth.service.JwtTokenProvider;
import com.uip.backend.auth.service.UipUserDetailsService;
import com.uip.backend.common.service.EmailService;
import com.uip.backend.tenant.api.dto.InviteUserRequest;
import com.uip.backend.tenant.domain.InviteToken;
import com.uip.backend.tenant.repository.InviteTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteService {

    private static final long INVITE_EXPIRY_HOURS = 48;
    private static final long MAX_INVITES_PER_HOUR = 10;

    private final InviteTokenRepository inviteTokenRepository;
    private final AppUserRepository appUserRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UipUserDetailsService userDetailsService;

    @Transactional
    public void createInvite(String tenantId, InviteUserRequest request, String invitedBy) {
        enforceRateLimit(tenantId);

        InviteToken token = InviteToken.builder()
                .tenantId(tenantId)
                .email(request.email())
                .role(request.role())
                .token(UUID.randomUUID())
                .invitedBy(invitedBy)
                .expiresAt(Instant.now().plusSeconds(INVITE_EXPIRY_HOURS * 3600))
                .createdAt(Instant.now())
                .build();
        inviteTokenRepository.save(token);

        emailService.sendInviteEmail(request.email(), token.getToken().toString());
        log.info("Invite created: tenant={} email={} role={} by={}",
                sanitizeLog(tenantId), sanitizeLog(request.email()),
                sanitizeLog(request.role()), sanitizeLog(invitedBy));
    }

    @Transactional
    public AuthResponse acceptInvite(UUID tokenValue, String password) {
        InviteToken token = inviteTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));

        if (token.isUsed()) {
            throw new IllegalArgumentException("Token already used");
        }
        if (token.isExpired()) {
            throw new IllegalArgumentException("Token has expired");
        }

        AppUser user = new AppUser(
                token.getEmail(),
                token.getEmail(),
                passwordEncoder.encode(password),
                UserRole.valueOf(token.getRole()),
                token.getTenantId(),
                "city." + token.getTenantId()
        );
        appUserRepository.save(user);

        token.setUsedAt(Instant.now());
        inviteTokenRepository.save(token);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails, user.getTenantId(), List.of(), List.of());
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);
        long expiresIn = jwtTokenProvider.getJwtProperties().getExpirationMs() / 1000;

        log.info("Invite accepted: email={} tenant={}", token.getEmail(), token.getTenantId());
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }

    private void enforceRateLimit(String tenantId) {
        long recentCount = inviteTokenRepository.countByTenantSince(tenantId, Instant.now().minusSeconds(3600));
        if (recentCount >= MAX_INVITES_PER_HOUR) {
            throw new IllegalStateException("Invite rate limit exceeded for tenant: " + tenantId);
        }
    }

    private static String sanitizeLog(String input) {
        if (input == null) return "null";
        return input.replaceAll("[\r\n\t]", "_");
    }
}
