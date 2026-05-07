package com.catrescue.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthForgotPasswordRequest(
        @NotBlank @Email @Size(max = 255) String email
) {
}
