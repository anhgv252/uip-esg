package com.uip.backend.tenant.service;

import com.uip.backend.tenant.api.dto.TenantConfigResponse;
import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.domain.Tenant;
import com.uip.backend.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantConfigServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantConfigService service;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getCurrentTenantConfig")
    class GetConfig {

        @Test
        @DisplayName("should return tenant config when tenant found")
        void shouldReturnConfigWhenFound() {
            TenantContext.setCurrentTenant("hcm");

            Tenant tenant = new Tenant();
            tenant.setId(UUID.randomUUID());
            tenant.setTenantId("hcm");
            tenant.setTenantName("Ho Chi Minh City");
            tenant.setTier("T2");
            when(tenantRepository.findByTenantId("hcm")).thenReturn(Optional.of(tenant));

            TenantConfigResponse response = service.getCurrentTenantConfig();

            assertThat(response.getTenantId()).isEqualTo("hcm");
            assertThat(response.getFeatures()).isNotEmpty();
            assertThat(response.getBranding().getPartnerName()).isEqualTo("UIP Smart City");
            assertThat(response.getBranding().getPrimaryColor()).isEqualTo("#1976D2");
        }

        @Test
        @DisplayName("should return default config when tenant not found")
        void shouldReturnDefaultWhenNotFound() {
            TenantContext.setCurrentTenant("unknown");
            when(tenantRepository.findByTenantId("unknown")).thenReturn(Optional.empty());

            TenantConfigResponse response = service.getCurrentTenantConfig();

            assertThat(response.getTenantId()).isEqualTo("default");
            assertThat(response.getFeatures()).hasSize(6);
            assertThat(response.getFeatures().get("esg-module").isEnabled()).isTrue();
            assertThat(response.getFeatures().get("citizen-portal").isEnabled()).isTrue();
            assertThat(response.getFeatures().get("ai-workflow").isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return default config when TenantContext is default")
        void shouldReturnDefaultForDefaultTenant() {
            TenantContext.setCurrentTenant("default");
            when(tenantRepository.findByTenantId("default")).thenReturn(Optional.empty());

            TenantConfigResponse response = service.getCurrentTenantConfig();

            assertThat(response.getTenantId()).isEqualTo("default");
        }

        @Test
        @DisplayName("should return default config when TenantContext is null")
        void shouldReturnDefaultWhenContextNull() {
            // TenantContext not set
            when(tenantRepository.findByTenantId(null)).thenReturn(Optional.empty());

            TenantConfigResponse response = service.getCurrentTenantConfig();

            assertThat(response.getTenantId()).isEqualTo("default");
            assertThat(response.getFeatures()).isNotEmpty();
        }

        @Test
        @DisplayName("all default features should be enabled")
        void allDefaultFeaturesEnabled() {
            TenantContext.setCurrentTenant("default");
            when(tenantRepository.findByTenantId("default")).thenReturn(Optional.empty());

            TenantConfigResponse response = service.getCurrentTenantConfig();

            response.getFeatures().forEach((key, flag) ->
                assertThat(flag.isEnabled())
                    .as("Feature '%s' should be enabled by default", key)
                    .isTrue()
            );
        }
    }
}
