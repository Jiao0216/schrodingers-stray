package com.catrescue.api.tracking.dto;

import java.time.Instant;

public record SightingListItemResponse(
        Long sightingId,
        Long catId,
        String imageUrl,
        Instant occurredAt,
        String addressText,
        String dedupStatus,
        String dedupReason,
        Double similarityScore,
        String aiHealthSummary
) {
}
