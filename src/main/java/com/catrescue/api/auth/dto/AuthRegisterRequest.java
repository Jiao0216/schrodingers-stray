package com.catrescue.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 40) String phone,
        @NotBlank @Size(min = 4, max = 12) String verificationCode
) {
}
