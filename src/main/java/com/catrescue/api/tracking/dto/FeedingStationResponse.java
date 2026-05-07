package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record FeedingStationResponse(
        long id,
        String name,
        String address,
        double latitude,
        double longitude,
        Instant lastFedAt
) {
}
