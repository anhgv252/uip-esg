package com.uip.backend.auth.service;

import com.uip.backend.auth.api.dto.AuthResponse;
import com.uip.backend.auth.api.dto.LoginRequest;
import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.exception.InvalidCredentialsException;
import com.uip.backend.auth.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService — covers all role branches in resolveScopes(),
 * login() success/failure paths, and refresh() valid/invalid paths.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UipUserDetailsService userDetailsService;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks private AuthService authService;

    private UserDetails operatorDetails;

    @BeforeEach
    void setUp() {
        operatorDetails = new User("alice", "pw",
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
    }

    // ─── login() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("ADMIN user: resolves full admin scopes + tenant_id")
        void login_adminUser_resolvesAdminScopes() {
            AppUser admin = buildUser("alice", UserRole.ROLE_ADMIN, "hcm");
            when(userDetailsService.loadUserByUsername("alice")).thenReturn(operatorDetails);
            when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(admin));
            when(tokenProvider.generateAccessToken(any(), eq("hcm"), argThat(s -> s.contains("tenant:admin")), any()))
                    .thenReturn("admin-token");
            when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
            when(tokenProvider.getJwtProperties()).thenReturn(mockJwtProperties(900_000L));

            AuthResponse response = authService.login(new LoginRequest("alice", "pw"));

            assertThat(response.accessToken()).isEqualTo("admin-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            assertThat(response.expiresIn()).isEqualTo(900L);
            verify(tokenProvider).generateAccessToken(any(), eq("hcm"),
                    argThat(s -> s.contains("tenant:admin") && s.contains("environment:write")), any());
        }

        @Test
        @DisplayName("OPERATOR user: resolves operator scopes (no tenant:admin, no citizen:admin)")
        void login_operatorUser_resolvesOperatorScopes() {
            AppUser operator = buildUser("bob", UserRole.ROLE_OPERATOR, "dn");
            when(userDetailsService.loadUserByUsername("bob"))
                    .thenReturn(new User("bob", "pw", List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
            when(appUserRepository.findByUsername("bob")).thenReturn(Optional.of(operator));
            when(tokenProvider.generateAccessToken(any(), eq("dn"),
                    argThat(s -> s.contains("esg:read") && !s.contains("tenant:admin")), any()))
                    .thenReturn("operator-token");
            when(tokenProvider.generateRefreshToken(any())).thenReturn("rt");
            when(tokenProvider.getJwtProperties()).thenReturn(mockJwtProperties(900_000L));

            AuthResponse response = authService.login(new LoginRequest("bob", "pw"));

            assertThat(response.accessToken()).isEqualTo("operator-token");
        }

        @Test
        @DisplayName("CITIZEN user: resolves read-only scopes")
        void login_citizenUser_resolvesReadOnlyScopes() {
            AppUser citizen = buildUser("carol", UserRole.ROLE_CITIZEN, "sg");
            when(userDetailsService.loadUserByUsername("carol"))
                    .thenReturn(new User("carol", "pw", List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))));
            when(appUserRepository.findByUsername("carol")).thenReturn(Optional.of(citizen));
            when(tokenProvider.generateAccessToken(any(), eq("sg"),
                    argThat(s -> s.contains("environment:read") && !s.contains("environment:write")), any()))
                    .thenReturn("citizen-token");
            when(tokenProvider.generateRefreshToken(any())).thenReturn("rt");
            when(tokenProvider.getJwtProperties()).thenReturn(mockJwtProperties(900_000L));

            AuthResponse response = authService.login(new LoginRequest("carol", "pw"));

            assertThat(response.accessToken()).isEqualTo("citizen-token");
        }

        @Test
        @DisplayName("No AppUser in DB: falls back to tenant='default' and empty scopes")
        void login_noAppUser_usesDefaults() {
            when(userDetailsService.loadUserByUsername("ghost")).thenReturn(operatorDetails);
            when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            when(tokenProvider.generateAccessToken(any(), eq("default"), eq(List.of()), eq(List.of())))
                    .thenReturn("ghost-token");
            when(tokenProvider.generateRefreshToken(any())).thenReturn("rt");
            when(tokenProvider.getJwtProperties()).thenReturn(mockJwtProperties(900_000L));

            AuthResponse response = authService.login(new LoginRequest("ghost", "pw"));

            assertThat(response.accessToken()).isEqualTo("ghost-token");
            verify(tokenProvider).generateAccessToken(any(), eq("default"), eq(List.of()), eq(List.of()));
        }

        @Test
        @DisplayName("Bad credentials: throws InvalidCredentialsException")
        void login_badCredentials_throwsInvalidCredentialsException() {
            doThrow(new BadCredentialsException("bad"))
                    .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

            assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("Invalid username or password");
        }
    }

    // ─── refresh() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("Valid refresh token: issues new access token")
        void refresh_validToken_issuesNewAccessToken() {
            AppUser operator = buildUser("alice", UserRole.ROLE_OPERATOR, "hcm");
            when(tokenProvider.extractUsername("rt")).thenReturn("alice");
            when(userDetailsService.loadUserByUsername("alice")).thenReturn(operatorDetails);
            when(tokenProvider.isRefreshTokenValid("rt", operatorDetails)).thenReturn(true);
            when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(operator));
            when(tokenProvider.generateAccessToken(any(), eq("hcm"), any(), any()))
                    .thenReturn("new-access-token");
            when(tokenProvider.getJwtProperties()).thenReturn(mockJwtProperties(900_000L));

            AuthResponse response = authService.refresh("rt");

            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isEqualTo("rt");
        }

        @Test
        @DisplayName("Invalid/expired refresh token: throws InvalidCredentialsException")
        void refresh_invalidToken_throwsException() {
            when(tokenProvider.extractUsername("bad-rt")).thenReturn("alice");
            when(userDetailsService.loadUserByUsername("alice")).thenReturn(operatorDetails);
            when(tokenProvider.isRefreshTokenValid("bad-rt", operatorDetails)).thenReturn(false);

            assertThatThrownBy(() -> authService.refresh("bad-rt"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("Invalid or expired refresh token");
        }

        @Test
        @DisplayName("No AppUser on refresh: falls back to tenant='default' and empty scopes")
        void refresh_noAppUser_usesDefaults() {
            when(tokenProvider.extractUsername("rt")).thenReturn("ghost");
            when(userDetailsService.loadUserByUsername("ghost")).thenReturn(operatorDetails);
            when(tokenProvider.isRefreshTokenValid("rt", operatorDetails)).thenReturn(true);
            when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            when(tokenProvider.generateAccessToken(any(), eq("default"), eq(List.of()), eq(List.of())))
                    .thenReturn("new-token");
            when(tokenProvider.getJwtProperties()).thenReturn(mockJwtProperties(900_000L));

            AuthResponse response = authService.refresh("rt");

            assertThat(response.accessToken()).isEqualTo("new-token");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private AppUser buildUser(String username, UserRole role, String tenantId) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setRole(role);
        u.setTenantId(tenantId);
        return u;
    }

    private com.uip.backend.auth.config.JwtProperties mockJwtProperties(long expirationMs) {
        com.uip.backend.auth.config.JwtProperties props =
                mock(com.uip.backend.auth.config.JwtProperties.class);
        when(props.getExpirationMs()).thenReturn(expirationMs);
        return props;
    }
}
