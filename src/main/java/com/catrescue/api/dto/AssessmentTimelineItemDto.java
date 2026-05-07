package com.catrescue.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AssessmentTimelineItemDto(
        UUID id,
        Instant createdAt,
        String locationSummary,
        String healthStatus,
        String neuteredStatus,
        String thumbnailUrl
) {
}
