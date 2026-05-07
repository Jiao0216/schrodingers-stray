package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record NewSightingCommand(
        Long reporterUserId,
        String imageUrl,
        String addressText,
        double latitude,
        double longitude,
        Instant occurredAt
) {
}
