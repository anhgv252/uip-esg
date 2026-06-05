package com.uip.backend.bms.api;

import com.uip.backend.bms.adapter.BmsDiscoveryService;
import com.uip.backend.bms.api.dto.BmsDeviceRequest;
import com.uip.backend.bms.api.dto.BmsDeviceResponse;
import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.service.BmsDeviceService;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/bms/devices")
@RequiredArgsConstructor
@Tag(name = "BMS Devices", description = "Building Management System device management")
@SecurityRequirement(name = "Bearer Authentication")
public class BmsDeviceController {

    private final BmsDeviceService bmsDeviceService;
    private final BmsDiscoveryService bmsDiscoveryService;

    @GetMapping
    @Operation(summary = "List all BMS devices for current tenant")
    public ResponseEntity<List<BmsDeviceResponse>> listDevices() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(bmsDeviceService.listDevices(tenantId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get BMS device by ID")
    public ResponseEntity<BmsDeviceResponse> getDevice(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        return bmsDeviceService.getDevice(tenantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create or update a BMS device (idempotent upsert)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Device created"),
        @ApiResponse(responseCode = "200", description = "Device updated (already existed)"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "403", description = "Tenant context required")
    })
    public ResponseEntity<BmsDeviceResponse> createDevice(@Valid @RequestBody BmsDeviceRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        BmsDevice device = bmsDeviceService.upsertDevice(tenantId, request);
        boolean created = device.getCreatedAt().equals(device.getUpdatedAt());
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK)
                .body(BmsDeviceResponse.from(device));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a BMS device")
    public ResponseEntity<BmsDeviceResponse> updateDevice(@PathVariable UUID id,
                                                          @Valid @RequestBody BmsDeviceRequest request) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(BmsDeviceResponse.from(bmsDeviceService.updateDevice(tenantId, id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a BMS device")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDevice(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        bmsDeviceService.deleteDevice(tenantId, id);
    }

    @PostMapping("/discover")
    @Operation(summary = "Trigger BACnet Who-Is discovery scan")
    public ResponseEntity<List<BmsDeviceResponse>> discoverDevices(
            @RequestParam(defaultValue = "255.255.255.255") String broadcast,
            @RequestParam(defaultValue = "100") int localDeviceId) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(403).build();
        }
        List<BmsDevice> discovered = bmsDiscoveryService.discoverDevices(tenantId, broadcast, localDeviceId);
        return ResponseEntity.ok(discovered.stream().map(BmsDeviceResponse::from).toList());
    }
}
