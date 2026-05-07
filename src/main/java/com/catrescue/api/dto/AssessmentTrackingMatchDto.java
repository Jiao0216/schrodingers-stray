package com.catrescue.api.dto;

/**
 * Community tracking match when an assessment upload is also reported as a sighting
 * (same image + coordinates + reporter user).
 */
public record AssessmentTrackingMatchDto(
        Long sightingId,
        Long catId,
        String dedupStatus,
        Long suggestedCatId,
        Long duplicateOfSightingId,
        Double similarityScore,
        String dedupReason,
        /** Distinct other cats with sightings within ~1.5km in the heatmap window (excludes {@code catId}). */
        int otherCatsNearbyApprox
) {
}
