package com.uip.backend.tenant;

import com.uip.backend.auth.domain.AppUser;
import com.uip.backend.auth.domain.UserRole;
import com.uip.backend.auth.repository.AppUserRepository;
import com.uip.backend.tenant.api.dto.TenantSettingsDto;
import com.uip.backend.tenant.api.dto.TenantUserDto;
import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.domain.Tenant;
import com.uip.backend.tenant.domain.TenantConfigEntry;
import com.uip.backend.tenant.domain.TenantConfigEntryId;
import com.uip.backend.tenant.repository.TenantConfigRepository;
import com.uip.backend.tenant.repository.TenantRepository;
import com.uip.backend.tenant.service.InviteService;
import com.uip.backend.tenant.service.TenantAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAdminServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private InviteService inviteService;

    @InjectMocks
    private TenantAdminService service;

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private Authentication mockAuth(String... authorities) {
        Authentication auth = mock(Authentication.class);
        java.util.Collection<org.springframework.security.core.GrantedAuthority> grantedAuthorities =
                Arrays.stream(authorities)
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .collect(java.util.stream.Collectors.toList());
        // Use doReturn/when to avoid generic wildcard capture issue
        doReturn(grantedAuthorities).when(auth).getAuthorities();
        return auth;
    }

    // ---- resolveEffectiveTenantId ----

    @Nested
    @DisplayName("resolveEffectiveTenantId")
    class ResolveEffectiveTenantId {

        @Test
        @DisplayName("ROLE_ADMIN returns path param")
        void adminReturnsPathParam() {
            Authentication auth = mockAuth("ROLE_ADMIN");

            String result = service.resolveEffectiveTenantId("tenant-path", auth);

            assertThat(result).isEqualTo("tenant-path");
        }

        @Test
        @DisplayName("ROLE_TENANT_ADMIN with matching path returns JWT tenant")
        void tenantAdminMatchingPathReturnsJwtTenant() {
            Authentication auth = mockAuth("ROLE_TENANT_ADMIN");

            try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
                ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-jwt");

                String result = service.resolveEffectiveTenantId("tenant-jwt", auth);

                assertThat(result).isEqualTo("tenant-jwt");
            }
        }

        @Test
        @DisplayName("ROLE_TENANT_ADMIN with mismatched path returns JWT tenant (ignores path)")
        void tenantAdminMismatchedPathReturnsJwtTenant() {
            Authentication auth = mockAuth("ROLE_TENANT_ADMIN");

            try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
                ctx.when(TenantContext::getCurrentTenant).thenReturn("tenant-jwt");

                String result = service.resolveEffectiveTenantId("other-tenant", auth);

                assertThat(result).isEqualTo("tenant-jwt");
            }
        }

        @Test
        @DisplayName("ROLE_OPERATOR throws AccessDeniedException")
        void operatorThrowsAccessDenied() {
            Authentication auth = mockAuth("ROLE_OPERATOR");

            assertThatThrownBy(() -> service.resolveEffectiveTenantId("any", auth))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Access denied");
        }
    }

    // ---- listUsers ----

    @Nested
    @DisplayName("listUsers")
    class ListUsers {

        @Test
        @DisplayName("returns mapped DTOs")
        void returnsMappedDtos() {
            AppUser user1 = new AppUser("alice", "alice@test.com", "hash", UserRole.ROLE_TENANT_ADMIN, "t1", "city.t1");
            AppUser user2 = new AppUser("bob", "bob@test.com", "hash", UserRole.ROLE_OPERATOR, "t1", "city.t1");

            when(appUserRepository.findByTenantId("t1")).thenReturn(List.of(user1, user2));

            List<TenantUserDto> result = service.listUsers("t1");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).username()).isEqualTo("alice");
            assertThat(result.get(0).role()).isEqualTo("ROLE_TENANT_ADMIN");
            assertThat(result.get(0).tenantId()).isEqualTo("t1");
            assertThat(result.get(1).username()).isEqualTo("bob");
            assertThat(result.get(1).role()).isEqualTo("ROLE_OPERATOR");
        }
    }

    // ---- updateUserRole ----

    @Nested
    @DisplayName("updateUserRole")
    class UpdateUserRole {

        @Test
        @DisplayName("valid user in tenant updates role")
        void validUserUpdatesRole() {
            UUID userId = UUID.randomUUID();
            AppUser user = new AppUser("alice", "alice@test.com", "hash", UserRole.ROLE_OPERATOR, "t1", "city.t1");

            when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

            service.updateUserRole("t1", userId, "ROLE_TENANT_ADMIN");

            assertThat(user.getRole()).isEqualTo(UserRole.ROLE_TENANT_ADMIN);
        }

        @Test
        @DisplayName("user not in tenant throws AccessDeniedException")
        void userNotInTenantThrowsAccessDenied() {
            UUID userId = UUID.randomUUID();
            AppUser user = new AppUser("alice", "alice@test.com", "hash", UserRole.ROLE_OPERATOR, "t1", "city.t1");

            when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.updateUserRole("other-tenant", userId, "ROLE_TENANT_ADMIN"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("does not belong");
        }
    }

    // ---- getSettings ----

    @Nested
    @DisplayName("getSettings")
    class GetSettings {

        @Test
        @DisplayName("returns merged KV + branding")
        void returnsMergedKvAndBranding() {
            Tenant tenant = new Tenant();
            tenant.setTenantId("t1");
            tenant.setTenantName("Test Partner");

            TenantConfigEntry entry = TenantConfigEntry.builder()
                    .tenantId("t1")
                    .configKey("notify.email")
                    .configValue("true")
                    .updatedBy("admin")
                    .updatedAt(Instant.now())
                    .build();

            when(tenantRepository.findByTenantId("t1")).thenReturn(Optional.of(tenant));
            when(tenantConfigRepository.findByTenantId("t1")).thenReturn(List.of(entry));

            TenantSettingsDto result = service.getSettings("t1");

            assertThat(result.tenantId()).isEqualTo("t1");
            assertThat(result.configEntries()).containsEntry("notify.email", "true");
            assertThat(result.branding().partnerName()).isEqualTo("Test Partner");
            assertThat(result.branding().primaryColor()).isEqualTo("#1976D2");
            assertThat(result.branding().logoUrl()).isNull();
        }
    }

    // ---- updateSettings ----

    @Nested
    @DisplayName("updateSettings")
    class UpdateSettings {

        @Test
        @DisplayName("new key creates entry")
        void newKeyCreatesEntry() {
            TenantConfigEntryId id = new TenantConfigEntryId("t1", "new.key");

            when(tenantConfigRepository.findById(id)).thenReturn(Optional.empty());
            when(tenantConfigRepository.save(any(TenantConfigEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateSettings("t1", "new.key", "value1", "admin");

            verify(tenantConfigRepository).save(argThat(entry ->
                    entry.getTenantId().equals("t1")
                    && entry.getConfigKey().equals("new.key")
                    && entry.getConfigValue().equals("value1")
                    && entry.getUpdatedBy().equals("admin")
            ));
        }

        @Test
        @DisplayName("existing key updates value")
        void existingKeyUpdatesValue() {
            TenantConfigEntryId id = new TenantConfigEntryId("t1", "existing.key");
            TenantConfigEntry existing = TenantConfigEntry.builder()
                    .tenantId("t1")
                    .configKey("existing.key")
                    .configValue("old-value")
                    .updatedBy("previous-admin")
                    .updatedAt(Instant.now().minusSeconds(60))
                    .build();

            when(tenantConfigRepository.findById(id)).thenReturn(Optional.of(existing));
            when(tenantConfigRepository.save(any(TenantConfigEntry.class))).thenAnswer(inv -> inv.getArgument(0));

            service.updateSettings("t1", "existing.key", "new-value", "admin");

            assertThat(existing.getConfigValue()).isEqualTo("new-value");
            assertThat(existing.getUpdatedBy()).isEqualTo("admin");
            verify(tenantConfigRepository).save(same(existing));
        }
    }
}
