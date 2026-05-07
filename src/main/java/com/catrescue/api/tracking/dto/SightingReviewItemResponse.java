package com.catrescue.api.tracking.dto;

import com.catrescue.api.tracking.domain.DedupStatus;

import java.time.Instant;
import java.util.List;

public record SightingReviewItemResponse(
        Long sightingId,
        Long catId,
        Long reporterUserId,
        String imageUrl,
        String addressText,
        double latitude,
        double longitude,
        Instant occurredAt,
        String coatColor,
        String patternType,
        String bodySize,
        List<String> specialFeatures,
        boolean earTipped,
        double earTippedConfidence,
        DedupStatus dedupStatus,
        Long suggestedCatId,
        Long duplicateOfSightingId,
        Double similarityScore,
        String dedupReason,
        Instant createdAt
) {
}
