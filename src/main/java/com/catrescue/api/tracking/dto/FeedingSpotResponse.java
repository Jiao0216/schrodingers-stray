package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record FeedingSpotResponse(
        Long id,
        double latitude,
        double longitude,
        String description,
        Long createdByUserId,
        Instant createdAt,
        boolean active,
        String photoUrl,
        Double distanceMeters,
        Instant lastFedAt,
        int recentCheckInCount
) {
}
