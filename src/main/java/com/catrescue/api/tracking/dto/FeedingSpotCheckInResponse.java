package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record FeedingSpotCheckInResponse(
        boolean success,
        Instant fedAt,
        Long recordId
) {
}
