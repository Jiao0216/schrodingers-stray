package com.catrescue.api.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ReportSightingRequest(
        @NotNull Long reporterUserId,
        @NotBlank String imageUrl,
        String addressText,
        @NotNull Double latitude,
        @NotNull Double longitude,
        Instant occurredAt
) {
}
