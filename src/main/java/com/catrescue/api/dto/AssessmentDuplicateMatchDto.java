package com.catrescue.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shown after upload when this photo matches a prior assessment (&gt;70% feature similarity).
 */
public record AssessmentDuplicateMatchDto(
        UUID matchedAssessmentId,
        double similarityScore,
        Instant matchedCreatedAt,
        String matchedLocationSummary,
        List<AssessmentTimelineItemDto> clusterSightings
) {
}
