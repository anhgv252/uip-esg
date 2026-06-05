package com.uip.backend.bms.api;

import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.service.BmsDeviceCommandService;
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

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/bms/devices")
@RequiredArgsConstructor
@Tag(name = "BMS Device Commands", description = "Send commands to BMS devices via MQTT")
@SecurityRequirement(name = "Bearer Authentication")
public class BmsDeviceCommandController {

    private final BmsDeviceCommandService commandService;

    @PostMapping("/{id}/commands")
    @Operation(summary = "Send command to BMS device (fire-and-forget, HTTP 202)")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Command accepted for processing"),
        @ApiResponse(responseCode = "400", description = "Invalid command payload"),
        @ApiResponse(responseCode = "403", description = "Tenant context required"),
        @ApiResponse(responseCode = "404", description = "Device not found")
    })
    public ResponseEntity<Map<String, Object>> sendCommand(
            @PathVariable UUID id,
            @Valid @RequestBody BmsCommand command) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(403).build();
        }

        String commandId = commandService.sendCommand(tenantId, id, command);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "commandId", commandId,
                "status", "ACCEPTED",
                "timestamp", java.time.Instant.now().toString()
        ));
    }
}
