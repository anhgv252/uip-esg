package com.uip.backend.tenant.api;

import com.uip.backend.auth.api.dto.AuthResponse;
import com.uip.backend.tenant.api.dto.AcceptInviteRequest;
import com.uip.backend.tenant.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/invite")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Invite", description = "User invitation acceptance")
public class InviteController {

    private final InviteService inviteService;

    @PostMapping("/accept")
    @Operation(summary = "Accept an invitation and set password (public endpoint)")
    public ResponseEntity<AuthResponse> acceptInvite(
            @RequestBody @jakarta.validation.Valid AcceptInviteRequest request) {
        AuthResponse response = inviteService.acceptInvite(request.token(), request.password());
        return ResponseEntity.ok(response);
    }
}
