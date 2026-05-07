package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record FeedingRecordResponse(
        long id,
        long catId,
        Instant fedAt,
        Long reporterUserId,
        String notes,
        Instant createdAt
) {
}
