package com.uip.backend.tenant.api;

import com.uip.backend.tenant.api.dto.TenantConfigResponse;
import com.uip.backend.tenant.service.TenantConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
public class TenantConfigController {

    private final TenantConfigService tenantConfigService;

    @GetMapping("/config")
    public ResponseEntity<TenantConfigResponse> getTenantConfig() {
        return ResponseEntity.ok(tenantConfigService.getCurrentTenantConfig());
    }
}
