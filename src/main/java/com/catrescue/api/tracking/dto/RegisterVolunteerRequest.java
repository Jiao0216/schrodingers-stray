package com.catrescue.api.tracking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterVolunteerRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 40) String phone,
        Double serviceLatitude,
        Double serviceLongitude,
        @Size(max = 2000) String bio
) {
}
