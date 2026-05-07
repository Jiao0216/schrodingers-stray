package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record VolunteerLatestCatResponse(
        Long catId,
        String catName,
        String imageUrl,
        Instant occurredAt,
        String addressText,
        String sterilizationStatus,
        boolean earTipped
) {
}
