package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record FeedingSpotRecordResponse(
        Long id,
        Long feedingSpotId,
        Long fedByUserId,
        Instant fedAt,
        String note
) {
}
