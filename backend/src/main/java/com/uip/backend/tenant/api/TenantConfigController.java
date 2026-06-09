package com.uip.backend.tenant.api;

import com.uip.backend.tenant.api.dto.TenantConfigResponse;
import com.uip.backend.tenant.service.TenantConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant Config", description = "Tenant configuration lookup")
public class TenantConfigController {

    private final TenantConfigService tenantConfigService;

    @GetMapping("/config")
    @Operation(summary = "Get current tenant configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tenant config returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<TenantConfigResponse> getTenantConfig() {
        return ResponseEntity.ok(tenantConfigService.getCurrentTenantConfig());
    }
}
