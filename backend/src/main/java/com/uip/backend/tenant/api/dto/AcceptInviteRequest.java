package com.uip.backend.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
public record AcceptInviteRequest(
        @NotNull UUID token,
        @NotBlank @Size(min = 8) String password
) {}
