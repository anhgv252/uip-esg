package com.uip.backend.citizen.api.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenRegistrationRequest {
    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 255, message = "Full name must be between 3 and 255 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^(\\+84|0)[35789][0-9]{8}$",
             message = "Phone must be a valid Vietnamese number")
    private String phone;

    @NotBlank(message = "CCCD/ID is required")
    @Pattern(regexp = "^\\d{9}(\\d{3})?$",
             message = "CCCD must be 9 or 12 digits")
    private String cccd;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
