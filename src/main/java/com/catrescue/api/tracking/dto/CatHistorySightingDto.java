package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record CatHistorySightingDto(
        long sightingId,
        Instant occurredAt,
        double latitude,
        double longitude,
        String addressText,
        boolean earTipped,
        double earTippedConfidence
) {
}
