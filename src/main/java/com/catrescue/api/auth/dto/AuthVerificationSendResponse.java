package com.catrescue.api.auth.dto;

public record AuthVerificationSendResponse(
        boolean sent,
        String message
) {
}
