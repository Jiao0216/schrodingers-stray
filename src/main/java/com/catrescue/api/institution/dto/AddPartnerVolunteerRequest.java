package com.catrescue.api.institution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddPartnerVolunteerRequest(
        @NotBlank @Email String email,
        String displayName,
        String roleTag
) {
}
