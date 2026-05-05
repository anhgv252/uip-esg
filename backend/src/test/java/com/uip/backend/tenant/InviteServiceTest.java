package com.uip.backend.tenant;

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
import com.uip.backend.tenant.service.InviteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock
    private InviteTokenRepository inviteTokenRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UipUserDetailsService userDetailsService;

    @InjectMocks
    private InviteService service;

    private static final String TENANT_ID = "tenant-test";
    private static final String EMAIL = "newuser@example.com";
    private static final String ROLE = "ROLE_OPERATOR";
    private static final String INVITED_BY = "admin";

    // ---- helpers ----

    private InviteUserRequest buildRequest() {
        return InviteUserRequest.builder()
                .email(EMAIL)
                .role(ROLE)
                .build();
    }

    private InviteToken buildValidToken() {
        return InviteToken.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .email(EMAIL)
                .role(ROLE)
                .token(UUID.randomUUID())
                .invitedBy(INVITED_BY)
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();
    }

    private InviteToken buildExpiredToken() {
        InviteToken token = buildValidToken();
        token.setExpiresAt(Instant.now().minusSeconds(3600));
        return token;
    }

    private InviteToken buildUsedToken() {
        InviteToken token = buildValidToken();
        token.setUsedAt(Instant.now().minusSeconds(60));
        return token;
    }

    // ---- createInvite ----

    @Nested
    @DisplayName("createInvite")
    class CreateInvite {

        @Test
        @DisplayName("valid data creates token and sends email")
        void validDataCreatesTokenAndSendsEmail() {
            when(inviteTokenRepository.countByTenantSince(eq(TENANT_ID), any(Instant.class)))
                    .thenReturn(0L);
            when(inviteTokenRepository.save(any(InviteToken.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.createInvite(TENANT_ID, buildRequest(), INVITED_BY);

            ArgumentCaptor<InviteToken> tokenCaptor = ArgumentCaptor.forClass(InviteToken.class);
            verify(inviteTokenRepository).save(tokenCaptor.capture());

            InviteToken saved = tokenCaptor.getValue();
            assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getRole()).isEqualTo(ROLE);
            assertThat(saved.getToken()).isNotNull();
            assertThat(saved.getInvitedBy()).isEqualTo(INVITED_BY);
            assertThat(saved.getExpiresAt()).isAfter(Instant.now());

            verify(emailService).sendInviteEmail(eq(EMAIL), anyString());
        }

        @Test
        @DisplayName("rate limit exceeded throws IllegalStateException")
        void rateLimitExceededThrows() {
            when(inviteTokenRepository.countByTenantSince(eq(TENANT_ID), any(Instant.class)))
                    .thenReturn(10L);

            assertThatThrownBy(() -> service.createInvite(TENANT_ID, buildRequest(), INVITED_BY))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rate limit");

            verify(inviteTokenRepository, never()).save(any());
            verify(emailService, never()).sendInviteEmail(anyString(), anyString());
        }
    }

    // ---- acceptInvite ----

    @Nested
    @DisplayName("acceptInvite")
    class AcceptInvite {

        @Test
        @DisplayName("valid token creates user and returns JWT")
        void validTokenCreatesUserAndReturnsJwt() {
            InviteToken token = buildValidToken();
            UUID tokenValue = token.getToken();

            when(inviteTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(token));
            when(passwordEncoder.encode("Password123")).thenReturn("encoded-hash");
            when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

            UserDetails userDetails = new User(EMAIL, "encoded-hash",
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(ROLE)));
            when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
            when(jwtTokenProvider.generateAccessToken(eq(userDetails), eq(TENANT_ID), eq(List.of()), eq(List.of())))
                    .thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(userDetails)).thenReturn("refresh-token");
            // JwtProperties is a @Component with @Value fields -- mock the return chain
            com.uip.backend.auth.config.JwtProperties jwtProps = mock(com.uip.backend.auth.config.JwtProperties.class);
            when(jwtProps.getExpirationMs()).thenReturn(3600_000L);
            when(jwtTokenProvider.getJwtProperties()).thenReturn(jwtProps);

            AuthResponse response = service.acceptInvite(tokenValue, "Password123");

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600L);

            // Verify user created with correct fields
            ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
            verify(appUserRepository).save(userCaptor.capture());
            AppUser createdUser = userCaptor.getValue();
            assertThat(createdUser.getEmail()).isEqualTo(EMAIL);
            assertThat(createdUser.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(createdUser.getTenantPath()).isEqualTo("city." + TENANT_ID);

            // Verify token marked used
            assertThat(token.getUsedAt()).isNotNull();
            verify(inviteTokenRepository).save(token);
        }

        @Test
        @DisplayName("expired token throws 'Token has expired'")
        void expiredTokenThrows() {
            InviteToken token = buildExpiredToken();
            UUID tokenValue = token.getToken();

            when(inviteTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.acceptInvite(tokenValue, "Password123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Token has expired");

            verify(appUserRepository, never()).save(any());
        }

        @Test
        @DisplayName("already used token throws 'Token already used'")
        void usedTokenThrows() {
            InviteToken token = buildUsedToken();
            UUID tokenValue = token.getToken();

            when(inviteTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.acceptInvite(tokenValue, "Password123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Token already used");

            verify(appUserRepository, never()).save(any());
        }

        @Test
        @DisplayName("invalid token throws 'Invalid invite token'")
        void invalidTokenThrows() {
            UUID tokenValue = UUID.randomUUID();

            when(inviteTokenRepository.findByToken(tokenValue)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.acceptInvite(tokenValue, "Password123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid invite token");

            verify(appUserRepository, never()).save(any());
        }
    }
}
